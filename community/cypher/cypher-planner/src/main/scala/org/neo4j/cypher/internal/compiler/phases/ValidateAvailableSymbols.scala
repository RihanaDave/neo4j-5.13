/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.phases

import org.neo4j.cypher.internal.expressions.CachedProperty
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.ScopeExpression
import org.neo4j.cypher.internal.logical.plans.Expand.VariablePredicate
import org.neo4j.cypher.internal.logical.plans.FindShortestPaths
import org.neo4j.cypher.internal.logical.plans.Foreach
import org.neo4j.cypher.internal.logical.plans.ForeachApply
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.NestedPlanExpression
import org.neo4j.cypher.internal.logical.plans.StatefulShortestPath
import org.neo4j.cypher.internal.logical.plans.TransactionCommandLogicalPlan
import org.neo4j.cypher.internal.logical.plans.set.CreatePattern
import org.neo4j.cypher.internal.rewriting.ValidatingCondition
import org.neo4j.cypher.internal.runtime.ast.RuntimeConstant
import org.neo4j.cypher.internal.util.Foldable.FoldableAny
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildrenNewAccForSiblings
import org.neo4j.cypher.internal.util.attribution.Id

/**
 * Sanity check to make sure variables that are read in a plan is available.
 * Note, current implementation is not complete.
 */
object ValidateAvailableSymbols extends ValidatingCondition {
  override def name: String = "ValidateAvailableSymbols"

  override def apply(input: Any): Seq[String] = {
    input.folder.treeFold(Seq.empty[String]) {
      case plan: LogicalPlan => acc => TraverseChildren(acc ++ doApply(plan, input))
    }
  }

  private def doApply(plan: LogicalPlan, input: Any): Seq[String] = {
    val unavailable = readVariables(plan).diff(availableVariables(plan))
    if (unavailable.nonEmpty) {
      Seq(
        s"""Plan references unavailable variables ${unavailable.mkString(", ")}
           |$plan
           |
           |Full plan:
           |$input
           |""".stripMargin
      )
    } else {
      Seq.empty
    }
  }

  /*
   * Returns variables that are available to read in the specified plan.
   * Note! Current implementation is not really correct,
   * but a quick way to cover a lot of cases without much effort.
   */
  private def availableVariables(plan: LogicalPlan): Set[String] = {
    plan.availableSymbols.map(_.name) ++
      plan.lhs.map(_.availableSymbols.map(_.name)).getOrElse(Set.empty) ++
      plan.rhs.map(_.availableSymbols.map(_.name)).getOrElse(Set.empty)
  }

  private def readVariables(plan: LogicalPlan): Set[String] = readVariables(plan, plan.id)

  /*
   * Returns variables that are read in the specified plan.
   * Note! This implementation is not complete.
   */
  private def readVariables(plan: LogicalPlan, id: Id): Set[String] = {
    plan.folder.treeFold(Set.empty[String]) {
      case otherPlan: LogicalPlan if otherPlan.id != id => acc => SkipChildren(acc)
      case v: LogicalVariable => acc =>
          SkipChildren(acc + v.name)
      case expression: ScopeExpression => acc =>
          TraverseChildrenNewAccForSiblings[Set[String]](acc, _ -- expression.introducedVariables.map(_.name))
      case predicate: VariablePredicate => acc =>
          TraverseChildrenNewAccForSiblings[Set[String]](acc, _ - predicate.variable.name)
      case foreach: Foreach =>
        val createVariables = foreach.mutations.foldLeft(Set.empty[String]) {
          case (vars, CreatePattern(commands)) => vars ++ commands.map(_.variable.name)
          case (vars, _)                       => vars
        }
        val excluded = createVariables + foreach.variable.name
        acc => TraverseChildrenNewAccForSiblings[Set[String]](acc, _ -- excluded)
      case foreach: ForeachApply => acc =>
          TraverseChildrenNewAccForSiblings[Set[String]](acc, _ - foreach.variable.name)
      case rc: RuntimeConstant => acc =>
          TraverseChildrenNewAccForSiblings[Set[String]](acc, _ - rc.variable.name)
      case cachedProp: CachedProperty => acc =>
          SkipChildren(acc + cachedProp.entityVariable.name)

      // No special reason to skip the following, other than to save time
      case _: StatefulShortestPath => acc =>
          SkipChildren(acc)
      case _: FindShortestPaths => acc =>
          SkipChildren(acc)
      case _: NestedPlanExpression => acc =>
          SkipChildren(acc)
      case _: TransactionCommandLogicalPlan => acc =>
          SkipChildren(acc)
    }
  }
}
