/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.ast

import org.neo4j.cypher.internal.ast.ReturnItems.ReturnVariables
import org.neo4j.cypher.internal.ast.Union.UnionMapping
import org.neo4j.cypher.internal.ast.semantics.Scope
import org.neo4j.cypher.internal.ast.semantics.SemanticAnalysisTooling
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.fromState
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.success
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.when
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckResult
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckable
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.ast.semantics.Symbol
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.ExpressionWithComputedDependencies
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.SubqueryVariableShadowing
import org.neo4j.cypher.internal.util.topDown

sealed trait Query extends Statement with SemanticCheckable with SemanticAnalysisTooling {
  def containsUpdates: Boolean
  def returnColumns: List[LogicalVariable] = returnVariables.explicitVariables.toList

  /**
   * All variables that are explicitly listed to be returned from this statement.
   * This also includes the information whether all other potentially existing variables in scope are also returned.
   */
  def returnVariables: ReturnVariables

  /**
   * Given the root scope for this query part,
   * looks up the final scope after the last clause
   */
  def finalScope(scope: Scope): Scope

  /**
   * Check this query part if it start with an importing WITH
   */
  def checkImportingWith: SemanticCheck

  /**
   * Semantic check for when this `Query` is in a subquery, and might import
   * variables from the `outer` scope
   */
  def semanticCheckInSubqueryContext(outer: SemanticState): SemanticCheck

  /**
   * True if this query part starts with an importing WITH (has incoming arguments)
   */
  def isCorrelated: Boolean

  /**
   * True iff this query part ends with a return clause.
   */
  def isReturning: Boolean

  /**
   * Exists and Count can omit the Return Statement
   * Count still requires it for Distinct Unions as in this case the count
   * changes based on which rows are distinct vs not
   */
  def semanticCheckInSubqueryExpressionContext(canOmitReturn: Boolean): SemanticCheck

  protected def checkUse(): SemanticCheck =
    whenState(_.features(SemanticFeature.UseAsSingleGraphSelector))(
      thenBranch = {
        val useClauses = folder.findAllByClass[UseGraph]
        val distinctGraphs = useClauses.map(_.expression).toSet
        if (distinctGraphs.size > 1)
          SemanticCheck.fromFunctionWithContext { (semanticState, context) =>
            SemanticCheckResult.error(
              semanticState,
              context.errorMessageProvider.createMultipleGraphReferencesError(),
              useClauses(1).position
            )
          }
        else
          success
      }
    )
}

case class SingleQuery(clauses: Seq[Clause])(val position: InputPosition) extends Query
    with SemanticAnalysisTooling {
  assert(clauses.nonEmpty)

  override def containsUpdates: Boolean =
    clauses.exists {
      case sub: SubqueryCall => sub.innerQuery.containsUpdates
      case call: CallClause  => !call.containsNoUpdates
      case _: UpdateClause   => true
      case _                 => false
    }

  override def returnVariables: ReturnVariables = clauses.last.returnVariables

  override def isCorrelated: Boolean = importWith.isDefined

  override def isReturning: Boolean = clauses.last match {
    case _: Return => true
    case _         => false
  }

  def importColumns: Seq[String] = importWith match {
    case Some(w) => w.returnItems.items.map(_.name)
    case _       => Seq.empty
  }

  def importWith: Option[With] = {
    def hasImportFormat(w: With) = w match {
      case With(false, ri, None, None, None, None, _) =>
        ri.items.forall(_.isPassThrough)
      case _ =>
        false
    }

    clausesExceptLeadingFrom
      .headOption.collect { case w: With if hasImportFormat(w) => w }
  }

  private def leadingNonImportWith: Option[With] = {
    if (importWith.isDefined)
      None
    else
      clausesExceptLeadingFrom.headOption.collect { case wth: With => wth }
  }

  private def leadingGraphSelection: Option[GraphSelection] =
    clauses.headOption.collect { case s: GraphSelection => s }

  def clausesExceptLeadingImportWith: Seq[Clause] = {
    // Find the first occurrence of the importWith clause and split the sequence by it
    val (beforeImportWith, afterIncludingImportWith) = clauses.span(clause => !importWith.contains(clause))

    // Remove the importWith clause and re-assemble the sequence
    beforeImportWith ++ afterIncludingImportWith.drop(1)
  }

  private def clausesExceptLeadingFrom: Seq[Clause] =
    clauses.filterNot(leadingGraphSelection.contains)

  private def clausesExceptLeadingFromAndImportWith: Seq[Clause] = {
    clausesExceptLeadingImportWith.filterNot(leadingGraphSelection.contains)
  }

  private def semanticCheckAbstract(
    clauses: Seq[Clause],
    clauseCheck: Seq[Clause] => SemanticCheck,
    canOmitReturnClause: Boolean = false
  ): SemanticCheck =
    checkStandaloneCall(clauses) chain
      withScopedState(clauseCheck(clauses)) chain
      checkOrder(clauses, canOmitReturnClause) chain
      checkNoCallInTransactionsAfterWriteClause(clauses) chain
      checkInputDataStream(clauses) chain
      checkUse() chain
      recordCurrentScope(this)

  override def semanticCheck: SemanticCheck =
    semanticCheckAbstract(clauses, checkClauses(_, None))

  /**
   * No outer scope is needed for checkClauses as we don't need to check the naming of any returned variables
   * as no variables from EXISTS / COUNT can be returned, unlike in CALL subqueries.
   */
  override def semanticCheckInSubqueryExpressionContext(canOmitReturn: Boolean): SemanticCheck =
    semanticCheckAbstract(clauses, checkClauses(_, None), canOmitReturnClause = canOmitReturn)

  override def checkImportingWith: SemanticCheck = importWith.foldSemanticCheck(_.semanticCheck)

  override def semanticCheckInSubqueryContext(outer: SemanticState): SemanticCheck = {
    def importVariables: SemanticCheck =
      importWith.foldSemanticCheck(wth =>
        wth.semanticCheckContinuation(outer.currentScope.scope) chain
          recordCurrentScope(wth)
      )

    checkIllegalImportWith chain
      checkLeadingFrom(outer) chain
      semanticCheckAbstract(
        clausesExceptLeadingFromAndImportWith,
        importVariables chain checkClauses(_, Some(outer.currentScope.scope))
      ) chain
      checkShadowedVariables(outer)
  }

  private def checkLeadingFrom(outer: SemanticState): SemanticCheck =
    leadingGraphSelection match {
      case Some(from) => withState(outer)(from.semanticCheck)
      case None       => success
    }

  private def checkIllegalImportWith: SemanticCheck = leadingNonImportWith.foldSemanticCheck { wth =>
    def err(msg: String): SemanticCheck =
      error(s"Importing WITH should consist only of simple references to outside variables. $msg.", wth.position)

    def checkReturnItems: SemanticCheck = {
      val hasAliases = wth.returnItems.items.exists(!_.isPassThrough)
      when(hasAliases) { err("Aliasing or expressions are not supported") }
    }

    def checkDistinct: SemanticCheck = when(wth.distinct) { err("DISTINCT is not allowed") }
    def checkOrderBy: SemanticCheck = wth.orderBy.foldSemanticCheck(_ => err("ORDER BY is not allowed"))
    def checkWhere: SemanticCheck = wth.where.foldSemanticCheck(_ => err("WHERE is not allowed"))
    def checkSkip: SemanticCheck = wth.skip.foldSemanticCheck(_ => err("SKIP is not allowed"))
    def checkLimit: SemanticCheck = wth.limit.foldSemanticCheck(_ => err("LIMIT is not allowed"))

    fromState { state =>
      val resultState = wth.returnItems.items.foldSemanticCheck(_.semanticCheck)(state)

      // [[ExpressionWithComputedDependencies]] do not carry their dependencies directly. Instead the dependencies are stored in the recorded scopes in the semantic state.
      // See also: [[computeDependenciesForExpressions]]
      val rewriter = topDown(Rewriter.lift {
        case x: ExpressionWithComputedDependencies =>
          val dependencies =
            resultState.state.recordedScopes(x.subqueryAstNode).declarationsAndDependencies.dependencies
          x.withComputedScopeDependencies(dependencies.map(_.asVariable))
      })

      val hasImports = wth.returnItems.includeExisting || wth.returnItems.items.exists { item =>
        rewriter.apply(item.expression).asInstanceOf[Expression].dependencies.nonEmpty
      }
      when(hasImports) {
        checkReturnItems chain
          checkDistinct chain
          checkWhere chain
          checkOrderBy chain
          checkSkip chain
          checkLimit
      }
    }
  }

  private def checkStandaloneCall(clauses: Seq[Clause]): SemanticCheck = {
    clauses match {
      case Seq(_: UnresolvedCall, where: With) =>
        error(
          "Cannot use standalone call with WHERE (instead use: `CALL ... WITH * WHERE ... RETURN *`)",
          where.position
        )
      case Seq(_: GraphSelection, _: UnresolvedCall) =>
        // USE clause and standalone procedure call
        success
      case all if all.size > 1 && all.exists(c => c.isInstanceOf[UnresolvedCall]) =>
        // Non-standalone procedure call should not allow YIELD *
        clauses.find {
          case uc: UnresolvedCall => uc.yieldAll
          case _                  => false
        }.map(c =>
          error("Cannot use `YIELD *` outside standalone call", c.position)
        )
          .getOrElse(success)
      case _ =>
        success
    }
  }

  private def checkOrder(clauses: Seq[Clause], canOmitReturnClause: Boolean): SemanticCheck =
    (s: SemanticState) => {
      val sequenceErrors = clauses.sliding(2).foldLeft(Vector.empty[SemanticError]) {
        case (semanticErrors, pair) =>
          val optError = pair match {
            case Seq(match1: Match, match2: Match) if match1.optional && !match2.optional =>
              Some(SemanticError(
                s"${match2.name} cannot follow OPTIONAL ${match1.name} (perhaps use a WITH clause between them)",
                match2.position
              ))
            case Seq(clause: Return, _) =>
              Some(SemanticError(s"${clause.name} can only be used at the end of the query", clause.position))
            case Seq(_: UpdateClause, _: UpdateClause) =>
              None
            case Seq(_: UpdateClause, _: With) =>
              None
            case Seq(_: UpdateClause, _: Return) =>
              None
            case Seq(update: UpdateClause, clause) =>
              Some(SemanticError(s"WITH is required between ${update.name} and ${clause.name}", clause.position))
            case _ =>
              None
          }
          optError.fold(semanticErrors)(semanticErrors :+ _)
      }

      val commandErrors =
        if (clauses.count(_.isInstanceOf[CommandClause]) > 1) {
          val missingYield = clauses.sliding(2).foldLeft(Vector.empty[SemanticError]) {
            case (semanticErrors, pair) =>
              val optError = pair match {
                case Seq(command: TransactionsCommandClause, clause: With) if command.yieldAll =>
                  Some(SemanticError(
                    s"When combining `${command.name}` with other show and/or terminate commands, `YIELD *` isn't permitted.",
                    clause.position
                  ))
                case Seq(_: CommandClause, clause: With) if clause.withType != AddedInRewrite => None
                case Seq(command: CommandClause, _) =>
                  Some(SemanticError(
                    s"When combining `${command.name}` with other show and/or terminate commands, `YIELD` is mandatory.",
                    command.position
                  ))
                case _ => None
              }
              optError.fold(semanticErrors)(semanticErrors :+ _)
          }

          val missingReturn = clauses.last match {
            case clause: Return if !clause.addedInRewrite => None
            case clause =>
              Some(SemanticError(
                "When combining show and/or terminate commands, `RETURN` isn't optional.",
                clause.position
              ))
          }

          missingYield ++ missingReturn
        } else Vector.empty[SemanticError]

      val validLastClauses =
        "a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD"

      val concludeError = clauses match {
        // standalone procedure call
        case Seq(_: CallClause)                    => None
        case Seq(_: GraphSelection, _: CallClause) => None

        case Seq() =>
          Some(SemanticError(s"Query must conclude with $validLastClauses", this.position))

        // otherwise
        case seq => seq.last match {
            case _: UpdateClause | _: Return | _: CommandClause                                              => None
            case subquery: SubqueryCall if !subquery.innerQuery.isReturning && subquery.reportParams.isEmpty => None
            case call: CallClause if call.returnVariables.explicitVariables.isEmpty && !call.yieldAll        => None
            case call: CallClause =>
              Some(SemanticError(s"Query cannot conclude with ${call.name} together with YIELD", call.position))
            case _ if canOmitReturnClause => None
            case clause =>
              Some(SemanticError(
                s"Query cannot conclude with ${clause.name} (must be $validLastClauses)",
                clause.position
              ))
          }
      }

      semantics.SemanticCheckResult(s, sequenceErrors ++ concludeError ++ commandErrors)
    }

  private def checkNoCallInTransactionsAfterWriteClause(clauses: Seq[Clause]): SemanticCheck = {
    case class Acc(precedingWrite: Boolean, errors: Seq[SemanticError])

    val Acc(_, errors) = clauses.foldLeft[Acc](Acc(precedingWrite = false, Seq.empty)) {
      case (Acc(precedingWrite, errors), callInTxs: SubqueryCall) if SubqueryCall.isTransactionalSubquery(callInTxs) =>
        if (precedingWrite) {
          Acc(
            precedingWrite,
            errors :+ SemanticError(
              "CALL { ... } IN TRANSACTIONS after a write clause is not supported",
              callInTxs.position
            )
          )
        } else {
          Acc(precedingWrite, errors)
        }
      case (acc, clause) => Acc(
          acc.precedingWrite || clause.folder.treeExists { case _: UpdateClause => true },
          acc.errors
        )
    }
    errors
  }

  private def checkClauses(clauses: Seq[Clause], outerScope: Option[Scope]): SemanticCheck = {
    val lastIndex = clauses.size - 1
    clauses.zipWithIndex.foldSemanticCheck {
      case (clause, idx) =>
        val next = SemanticCheck.fromState { _ =>
          clause match {
            case c: HorizonClause =>
              checkHorizon(c, outerScope)
            case _ =>
              clause.semanticCheck.map { checked =>
                val resultState = clause match {
                  case _: UpdateClause if idx == lastIndex =>
                    checked.state.newSiblingScope
                  case cc: CallClause
                    if cc.returnVariables.explicitVariables.isEmpty && !cc.yieldAll && idx == lastIndex =>
                    checked.state.newSiblingScope
                  case _ =>
                    checked.state
                }
                checked.copy(state = resultState)
              }
          }
        }

        next chain recordCurrentScope(clause)
    }
  }

  private def checkHorizon(clause: HorizonClause, outerScope: Option[Scope]): SemanticCheck = {
    for {
      closingResult <- clause.semanticCheck
      continuationResult <- clause.semanticCheckContinuation(closingResult.state.currentScope.scope, outerScope)
    } yield {
      semantics.SemanticCheckResult(continuationResult.state, closingResult.errors ++ continuationResult.errors)
    }
  }

  private def checkInputDataStream(clauses: Seq[Clause]): SemanticCheck = {
    val idsClauses = clauses.filter(_.isInstanceOf[InputDataStream])

    idsClauses.size match {
      case c if c > 1 =>
        error("There can be only one INPUT DATA STREAM in a query", idsClauses(1).position)
      case c if c == 1 =>
        if (clauses.head.isInstanceOf[InputDataStream]) {
          success
        } else {
          error("INPUT DATA STREAM must be the first clause in a query", idsClauses.head.position)
        }
      case _ => success
    }
  }

  private def checkShadowedVariables(outer: SemanticState): SemanticCheck = { inner: SemanticState =>
    val outerScopeSymbols: Map[String, Symbol] = outer.currentScope.scope.symbolTable
    val innerScopeSymbols: Map[String, Set[Symbol]] = inner.currentScope.scope.allSymbols

    def isShadowed(s: Symbol): Boolean =
      innerScopeSymbols.contains(s.name) &&
        !innerScopeSymbols(s.name).map(_.definition).contains(s.definition)

    val shadowedSymbols = outerScopeSymbols.collect {
      case (name, symbol) if isShadowed(symbol) =>
        name -> innerScopeSymbols(name).find(_.definition != symbol.definition).get.definition.asVariable.position
    }
    val stateWithNotifications = shadowedSymbols.foldLeft(inner) {
      case (state, (varName, pos)) =>
        state.addNotification(SubqueryVariableShadowing(pos, varName))
    }

    SemanticCheckResult.success(stateWithNotifications)
  }

  override def finalScope(scope: Scope): Scope =
    scope.children.last
}

object Union {

  /**
   * This defines a mapping of variables in both parts of the union to variables valid in the scope after the union.
   */
  case class UnionMapping(
    unionVariable: LogicalVariable,
    variableInLhs: LogicalVariable,
    variableInRhs: LogicalVariable
  )
}

sealed trait Union extends Query {
  def lhs: Query
  def rhs: SingleQuery

  def unionMappings: List[UnionMapping]

  override def returnVariables: ReturnVariables = ReturnVariables(
    // If either side of the UNION has a RETURN *,
    // then returnVariables.explicitVariables will not list all variables.
    // Instead, one has to inspect `finalScope` to find all variables.
    lhs.returnVariables.includeExisting || rhs.returnVariables.includeExisting,
    unionMappings.map(_.unionVariable)
  )

  def containsUpdates: Boolean = lhs.containsUpdates || rhs.containsUpdates

  private def semanticCheckAbstract(
    queryCheck: Query => SemanticCheck,
    singleQueryCheck: SingleQuery => SemanticCheck
  ): SemanticCheck =
    checkUnionAggregation chain
      withScopedState(queryCheck(lhs)) chain
      withScopedState(singleQueryCheck(rhs)) chain
      checkColumnNamesAgree chain
      defineUnionVariables chain
      checkInputDataStream chain
      checkNoCallInTransactionInsideUnion chain
      checkUse() chain
      SemanticState.recordCurrentScope(this)

  def semanticCheck: SemanticCheck =
    semanticCheckAbstract(
      lhs => SemanticCheck.nestedCheck(lhs.semanticCheck),
      rhs => rhs.semanticCheck
    )

  override def semanticCheckInSubqueryExpressionContext(canOmitReturn: Boolean): SemanticCheck =
    semanticCheckAbstract(
      lhs => SemanticCheck.nestedCheck(lhs.semanticCheckInSubqueryExpressionContext(canOmitReturn)),
      rhs => rhs.semanticCheckInSubqueryExpressionContext(canOmitReturn)
    )

  override def checkImportingWith: SemanticCheck =
    SemanticCheck.nestedCheck(lhs.checkImportingWith) chain
      rhs.checkImportingWith

  override def isCorrelated: Boolean = lhs.isCorrelated || rhs.isCorrelated

  override def isReturning: Boolean = rhs.isReturning // we assume lhs has the same value

  def semanticCheckInSubqueryContext(outer: SemanticState): SemanticCheck =
    semanticCheckAbstract(
      lhs => SemanticCheck.nestedCheck(lhs.semanticCheckInSubqueryContext(outer)),
      rhs => rhs.semanticCheckInSubqueryContext(outer)
    )

  private def defineUnionVariables: SemanticCheck = (state: SemanticState) => {
    var result = SemanticCheckResult.success(state)
    val scopeFromLhs = lhs.finalScope(state.scope(lhs).get)
    val scopeFromRhs = rhs.finalScope(state.scope(rhs).get)

    /**
     * Derived from UnionMapping, but only has the names of the variables in LHS and RHS,
     * since that is also the information we need here.
     */
    case class Mapping(
      unionVariable: LogicalVariable,
      variableInLhsName: String,
      variableInRhsName: String
    )

    val mappings = {
      // We need a Set since otherwise we would declare variables multiple times,
      // e.g. if they are listed, but there is also a *,
      // or if both branches have a *.
      val builder = Set.newBuilder[Mapping]
      unionMappings.foreach { um =>
        builder.addOne(Mapping(um.unionVariable, um.variableInLhs.name, um.variableInRhs.name))
      }
      // If there is a RETURN * in at least one of the UNION branches,
      // we need to find out what extra variables to include here.
      if (lhs.returnVariables.includeExisting) {
        scopeFromLhs.symbolNames.foreach { name =>
          builder.addOne(Mapping(Variable(name)(this.position), name, name))
        }
      }
      if (rhs.returnVariables.includeExisting) {
        scopeFromRhs.symbolNames.foreach { name =>
          builder.addOne(Mapping(Variable(name)(this.position), name, name))
        }
      }
      builder.result()
    }

    for {
      mapping <- mappings
      symbolFromLhs <- scopeFromLhs.symbol(mapping.variableInLhsName)
      symbolFromRhs <- scopeFromRhs.symbol(mapping.variableInRhsName)
    } yield {
      val unionType = symbolFromLhs.types.union(symbolFromRhs.types)
      result = result.state.declareVariable(mapping.unionVariable, unionType, unionVariable = true) match {
        case Left(err)        => SemanticCheckResult(result.state, err +: result.errors)
        case Right(nextState) => SemanticCheckResult(nextState, result.errors)
      }
    }
    result
  }

  override def finalScope(scope: Scope): Scope =
    // Union defines all return variables in its own scope using defineUnionVariables
    scope

  // Check that columns names agree between both parts of the union
  def checkColumnNamesAgree: SemanticCheck

  private def checkInputDataStream: SemanticCheck = (state: SemanticState) => {

    def checkSingleQuery(query: SingleQuery, state: SemanticState) = {
      val idsClause = query.clauses.find(_.isInstanceOf[InputDataStream])
      if (idsClause.isEmpty) {
        SemanticCheckResult.success(state)
      } else {
        SemanticCheckResult.error(
          state,
          SemanticError("INPUT DATA STREAM is not supported in UNION queries", idsClause.get.position)
        )
      }
    }

    val lhsResult = lhs match {
      case q: SingleQuery => checkSingleQuery(q, state)
      case _              => SemanticCheckResult.success(state)
    }

    val rhsResult = checkSingleQuery(rhs, state)
    SemanticCheckResult(state, lhsResult.errors ++ rhsResult.errors)
  }

  private def checkUnionAggregation: SemanticCheck = (lhs, this) match {
    case (_: SingleQuery, _)                                      => None
    case (_: UnionAll, _: UnionAll)                               => None
    case (_: UnionDistinct, _: UnionDistinct)                     => None
    case (_: ProjectingUnionAll, _: ProjectingUnionAll)           => None
    case (_: ProjectingUnionDistinct, _: ProjectingUnionDistinct) => None
    case _ => Some(SemanticError("Invalid combination of UNION and UNION ALL", position))
  }

  private def checkNoCallInTransactionInsideUnion: SemanticCheck = {
    val nestedCallInTransactions = Seq(lhs, rhs).flatMap { qp => SubqueryCall.findTransactionalSubquery(qp) }

    nestedCallInTransactions.foldSemanticCheck { nestedCallInTransactions =>
      error("CALL { ... } IN TRANSACTIONS in a UNION is not supported", nestedCallInTransactions.position)
    }
  }
}

/**
 * UnmappedUnion classes are directly produced by the parser.
 * When we do namespacing, we need to convert them the [[ProjectingUnion]].
 * ProjectingUnion is never produced by the parser.
 *
 * This has two reasons:
 * a) We capture how variables are projected from the two final scopes of the parts of the union to the scope
 *    after the union, before the Namespacer changes the names so that the Variable inside and outside of the union have different names
 *    and we would not find them any longer. The Namespacer will still change the name, but since we captured the Variable and not the
 *    name, we still have the correct projecting information.
 * b) We need to disable `checkColumnNamesAgree` for ProjectingUnion, because the names will actually not agree any more after the namespacing.
 *    This is not a problem though, since we would have failed earlier if the names did not agree originally.
 */
sealed trait UnmappedUnion extends Union {

  // A value instead of a def prevents us from creating new variables every time this is used.
  // This is helpful if the variable is used by reference from the semantic state.
  private var _unionMappings = {
    for {
      lhsCol <- lhs.returnColumns
      rhsCol <- rhs.returnColumns.find(_.name == lhsCol.name)
    } yield {
      // This assumes that lhs.returnColumns and rhs.returnColumns agree
      UnionMapping(Variable(lhsCol.name)(this.position), lhsCol, rhsCol)
    }
  }

  override def unionMappings: List[UnionMapping] = _unionMappings

  override def dup(children: Seq[AnyRef]): UnmappedUnion.this.type = {
    val res = super.dup(children)

    val thisLhsCols = lhs.returnColumns
    val thisRhsCols = rhs.returnColumns
    val resLhsCols = res.lhs.returnColumns
    val resRhsCols = res.rhs.returnColumns

    def containTheSameInstances[X <: AnyRef](a: Seq[X], b: Seq[X]): Boolean =
      a.forall(elemA => b.exists(elemB => elemA eq elemB)) && a.size == b.size

    // If we have not rewritten any return column (by reference equality), then we can simply reuse this.unionMappings.
    // This is important because the variables are used by reference from the semantic state.
    if (containTheSameInstances(thisLhsCols, resLhsCols) && containTheSameInstances(thisRhsCols, resRhsCols)) {
      res._unionMappings = this.unionMappings
    }

    res
  }

  override def checkColumnNamesAgree: SemanticCheck = (state: SemanticState) => {
    val myScope: Scope = state.currentScope.scope

    val lhsScope = if (lhs.isReturning) lhs.finalScope(myScope.children.head) else Scope.empty
    val rhsScope = if (rhs.isReturning) rhs.finalScope(myScope.children.last) else Scope.empty
    val errors =
      if (lhsScope.symbolNames == rhsScope.symbolNames) {
        Seq.empty
      } else {
        Seq(SemanticError("All sub queries in an UNION must have the same return column names", position))
      }
    semantics.SemanticCheckResult(state, errors)
  }
}

sealed trait ProjectingUnion extends Union {
  // If we have a ProjectingUnion we have already checked this before and now they have been rewritten to actually not match.
  override def checkColumnNamesAgree: SemanticCheck = SemanticCheck.success
}

final case class UnionAll(lhs: Query, rhs: SingleQuery)(val position: InputPosition) extends UnmappedUnion
final case class UnionDistinct(lhs: Query, rhs: SingleQuery)(val position: InputPosition) extends UnmappedUnion

final case class ProjectingUnionAll(lhs: Query, rhs: SingleQuery, unionMappings: List[UnionMapping])(
  val position: InputPosition
) extends ProjectingUnion

final case class ProjectingUnionDistinct(lhs: Query, rhs: SingleQuery, unionMappings: List[UnionMapping])(
  val position: InputPosition
) extends ProjectingUnion
