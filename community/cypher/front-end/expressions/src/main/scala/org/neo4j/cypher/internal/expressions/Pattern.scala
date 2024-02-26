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
package org.neo4j.cypher.internal.expressions

import org.neo4j.cypher.internal.expressions.PatternPart.Selector
import org.neo4j.cypher.internal.label_expressions.LabelExpression
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition

import scala.annotation.tailrec

/**
 * Represents a comma-separated list of pattern parts. Therefore, this is known in the parser as PatternList.
 * As we (in contrast to PatternParts) can use this to describe arbitrary shaped graph structures, GQL refers to these as graph patterns.
 */

sealed trait Pattern extends ASTNode {

  def patternParts: Seq[PatternPart]

  lazy val length: Int = this.folder.fold(0) {
    case RelationshipChain(_, _, _) => _ + 1
    case _                          => identity
  }
}

object Pattern {

  final case class ForMatch(patternParts: Seq[PatternPartWithSelector])(val position: InputPosition) extends Pattern

  final case class ForUpdate(patternParts: Seq[NonPrefixedPatternPart])(val position: InputPosition) extends Pattern

  sealed trait SemanticContext {
    def name: String = SemanticContext.name(this)
    def description: String = SemanticContext.description(this)
  }

  object SemanticContext {
    case object Match extends SemanticContext
    case object Merge extends SemanticContext
    case object Create extends SemanticContext
    case object Expression extends SemanticContext

    def name(ctx: SemanticContext): String = ctx match {
      case Match      => "MATCH"
      case Merge      => "MERGE"
      case Create     => "CREATE"
      case Expression => "expression"
    }

    def description(ctx: SemanticContext): String = ctx match {
      case Match      => "a MATCH clause"
      case Merge      => "a MERGE clause"
      case Create     => "a CREATE clause"
      case Expression => "an expression"
    }
  }
}

case class RelationshipsPattern(element: RelationshipChain)(val position: InputPosition) extends ASTNode

/**
 * Represents one part in the comma-separated list of a pattern.
 *
 * In the parser, this is just referred to as Pattern.
 * As we (in contrast to pattern lists) can only use this to describe linear graph structures, GQL refers to these as path patterns.
 */
sealed abstract class PatternPart extends ASTNode {
  def allVariables: Set[LogicalVariable]
  def element: PatternElement

  def isBounded: Boolean
  def isFixedLength: Boolean
}

sealed trait NonPrefixedPatternPart extends PatternPart

case class PatternPartWithSelector(selector: Selector, part: NonPrefixedPatternPart) extends PatternPart {
  override def position: InputPosition = part.position
  override def allVariables: Set[LogicalVariable] = part.allVariables
  override def element: PatternElement = part.element
  override def isBounded: Boolean = part.isBounded || selector.isBounded
  override def isFixedLength: Boolean = part.isFixedLength

  def isSelective: Boolean = selector.isBounded

  def replaceElement(newElement: PatternElement): PatternPartWithSelector = {
    def replaceInAnonymous(app: AnonymousPatternPart): AnonymousPatternPart = app match {
      case p: PathPatternPart          => p.copy(element = newElement)
      case s: ShortestPathsPatternPart => s.copy(element = newElement)(s.position)
    }

    def replaceInNonPrefixed(npp: NonPrefixedPatternPart): NonPrefixedPatternPart = npp match {
      case npp: NamedPatternPart     => npp.copy(patternPart = replaceInAnonymous(npp.patternPart))(npp.position)
      case app: AnonymousPatternPart => replaceInAnonymous(app)
    }

    copy(part = replaceInNonPrefixed(part))
  }
}

case class NamedPatternPart(variable: Variable, patternPart: AnonymousPatternPart)(val position: InputPosition)
    extends NonPrefixedPatternPart {
  override def element: PatternElement = patternPart.element

  override def allVariables: Set[LogicalVariable] = patternPart.allVariables + variable

  override def isBounded: Boolean = patternPart.isBounded

  override def isFixedLength: Boolean = patternPart.isFixedLength
}

sealed trait AnonymousPatternPart extends NonPrefixedPatternPart {
  override def allVariables: Set[LogicalVariable] = element.allVariables
}

case class PathPatternPart(element: PatternElement) extends AnonymousPatternPart {
  override def position: InputPosition = element.position
  override def isBounded: Boolean = element.isBounded
  override def isFixedLength: Boolean = element.isFixedLength

  override def dup(children: Seq[AnyRef]): this.type = {
    PathPatternPart(children.head.asInstanceOf[PatternElement]).asInstanceOf[this.type]
  }
}

case class ShortestPathsPatternPart(element: PatternElement, single: Boolean)(val position: InputPosition)
    extends AnonymousPatternPart {

  val name: String =
    if (single)
      "shortestPath"
    else
      "allShortestPaths"

  override def isBounded: Boolean = true
  override def isFixedLength: Boolean = false
}

object PatternPart {

  def apply(element: PatternElement): PathPatternPart =
    PathPatternPart(element)

  sealed trait Selector extends ASTNode {
    def prettified: String

    def isBounded: Boolean
  }

  sealed trait CountedSelector extends Selector {
    val count: UnsignedDecimalIntegerLiteral
  }

  sealed trait SelectiveSelector extends Selector

  case class AnyPath(count: UnsignedDecimalIntegerLiteral)(val position: InputPosition) extends SelectiveSelector
      with CountedSelector {
    override def prettified: String = s"ANY ${count.value} PATHS"

    override def isBounded: Boolean = true
  }

  case class AllPaths()(val position: InputPosition) extends Selector {
    override def prettified: String = "ALL PATHS"
    override def isBounded: Boolean = false
  }

  case class AnyShortestPath(count: UnsignedDecimalIntegerLiteral)(val position: InputPosition)
      extends SelectiveSelector
      with CountedSelector {
    override def prettified: String = s"SHORTEST ${count.value} PATHS"
    override def isBounded: Boolean = true
  }

  case class AllShortestPaths()(val position: InputPosition) extends SelectiveSelector {
    override def prettified: String = "ALL SHORTEST PATHS"
    override def isBounded: Boolean = true
  }

  case class ShortestGroups(count: UnsignedDecimalIntegerLiteral)(val position: InputPosition) extends SelectiveSelector
      with CountedSelector {
    override def prettified: String = s"SHORTEST ${count.value} PATH GROUPS"
    override def isBounded: Boolean = true
  }
}

/**
 * Contains a list of elements that are concatenated in the query.
 *
 * NOTE that the concatenation is recorded only in the order of the factors in the sequence.
 * That is that `factors(i)` is concatenated with `factors(i - 1)` and `factors(i + 1)` if they exist.
 */
case class PathConcatenation(factors: Seq[PathFactor])(val position: InputPosition) extends PatternElement {
  override def allVariables: Set[LogicalVariable] = factors.flatMap(_.allVariables).toSet

  override def variable: Option[LogicalVariable] = None

  override def isBounded: Boolean = factors.forall(_.isBounded)

  override def isFixedLength: Boolean = factors.forall(_.isFixedLength)
}

/**
 * Elements of this trait can be put next to each other to form a juxtaposition in a pattern.
 */
sealed trait PathFactor extends PatternElement

sealed trait PatternAtom extends ASTNode

case class QuantifiedPath(
  part: NonPrefixedPatternPart,
  quantifier: GraphPatternQuantifier,
  optionalWhereExpression: Option[Expression],
  variableGroupings: Set[VariableGrouping]
)(val position: InputPosition)
    extends PathFactor with PatternAtom {

  override def allVariables: Set[LogicalVariable] = variableGroupings.map(_.group)

  override def variable: Option[LogicalVariable] = None

  override def isBounded: Boolean = quantifier match {
    case FixedQuantifier(_)           => true
    case IntervalQuantifier(_, upper) => upper.nonEmpty
    case PlusQuantifier()             => false
    case StarQuantifier()             => false
  }

  override def isFixedLength: Boolean = quantifier match {
    case _: FixedQuantifier                                                         => true
    case IntervalQuantifier(Some(lower), Some(upper)) if lower.value == upper.value => true
    case _                                                                          => false
  }
}

object QuantifiedPath {

  def apply(
    part: NonPrefixedPatternPart,
    quantifier: GraphPatternQuantifier,
    optionalWhereExpression: Option[Expression]
  )(position: InputPosition): QuantifiedPath = {
    val entityBindings = part.allVariables.map(getGrouping(_, position))
    QuantifiedPath(part, quantifier, optionalWhereExpression, entityBindings)(position)
  }

  def getGrouping(innerVar: LogicalVariable, qppPosition: InputPosition): VariableGrouping = {
    VariableGrouping(innerVar.copyId, innerVar.withPosition(qppPosition))(qppPosition)
  }
}

/**
 * Describes a variable that is exposed from a [[QuantifiedPath]].
 *
 * @param singleton the singleton variable inside the QuantifiedPath.
 * @param group the group variable exposed outside of the QuantifiedPath.
 */
case class VariableGrouping(singleton: LogicalVariable, group: LogicalVariable)(val position: InputPosition)
    extends ASTNode

// We can currently parse these but not plan them. Therefore, we represent them in the AST but disallow them in semantic checking when concatenated and unwrap them otherwise.
case class ParenthesizedPath(
  part: NonPrefixedPatternPart,
  optionalWhereClause: Option[Expression]
)(val position: InputPosition)
    extends PathFactor with PatternAtom {

  override def allVariables: Set[LogicalVariable] = part.element.allVariables

  override def variable: Option[LogicalVariable] = None

  override def isBounded: Boolean = part.isBounded

  override def isFixedLength: Boolean = part.isFixedLength
}

object ParenthesizedPath {

  def apply(part: NonPrefixedPatternPart)(position: InputPosition): ParenthesizedPath =
    ParenthesizedPath(part, None)(position)
}

sealed abstract class PatternElement extends ASTNode {
  def allVariables: Set[LogicalVariable]
  def variable: Option[LogicalVariable]
  def isBounded: Boolean
  def isFixedLength: Boolean

  def isSingleNode = false
}

object PatternElement {

  /**
   * Returns the boundary nodes of this pattern element. Note, this does not work on QPPs directly.
   * Therefore, qpps need to have been padded before.
   */
  @tailrec
  def boundaryNodes(element: PatternElement): Set[LogicalVariable] = {
    element match {
      // Either we have a simple pattern
      case pattern: SimplePattern =>
        val allVars = pattern.allVariablesLeftToRight
        Set(allVars.head, allVars.last)
      // or non-simple patterns (QPPs) have been padded (see QppsHavePaddedNodes)
      case PathConcatenation(factors) =>
        val left = factors.head.asInstanceOf[SimplePattern].allVariablesLeftToRight.head
        val right = factors.last.asInstanceOf[SimplePattern].allVariablesLeftToRight.last
        Set(left, right)
      case ParenthesizedPath(part, _) => boundaryNodes(part.element)
      case _                          => throw new IllegalStateException()
    }
  }
}

/**
 * A part of the pattern that consists of alternating nodes and relationships, starting and ending in a node.
 */
sealed abstract class SimplePattern extends PathFactor {
  def allVariablesLeftToRight: Seq[LogicalVariable]
}

case class RelationshipChain(
  element: SimplePattern,
  relationship: RelationshipPattern,
  rightNode: NodePattern
)(val position: InputPosition)
    extends SimplePattern {

  override def variable: Option[LogicalVariable] = relationship.variable

  override def allVariables: Set[LogicalVariable] = element.allVariables ++ relationship.variable ++ rightNode.variable

  override def allVariablesLeftToRight: Seq[LogicalVariable] =
    element.allVariablesLeftToRight ++ relationship.variable.toSeq ++ rightNode.allVariablesLeftToRight

  override def isBounded: Boolean = relationship.isBounded && element.isBounded

  override def isFixedLength: Boolean = relationship.isSingleLength && element.isFixedLength && rightNode.isFixedLength

  @tailrec
  final def leftNode: NodePattern = element match {
    case node: NodePattern      => node
    case rel: RelationshipChain => rel.leftNode
  }
}

/**
 * Represents one node in a pattern.
 */
case class NodePattern(
  variable: Option[LogicalVariable],
  labelExpression: Option[LabelExpression],
  properties: Option[Expression],
  predicate: Option[Expression]
)(val position: InputPosition)
    extends SimplePattern with PatternAtom {

  override def allVariables: Set[LogicalVariable] = variable.toSet

  override def allVariablesLeftToRight: Seq[LogicalVariable] = variable.toSeq

  override def isSingleNode = true

  override def isBounded: Boolean = true

  override def isFixedLength: Boolean = true
}

/**
 * Represents one relationship (without its neighbouring nodes) in a pattern.
 */
case class RelationshipPattern(
  variable: Option[LogicalVariable],
  labelExpression: Option[LabelExpression],
  length: Option[Option[Range]],
  properties: Option[Expression],
  predicate: Option[Expression],
  direction: SemanticDirection
)(val position: InputPosition) extends ASTNode with PatternAtom {

  def isSingleLength: Boolean = length.isEmpty

  def isDirected: Boolean = direction != SemanticDirection.BOTH

  def isBounded: Boolean = length match {
    case Some(Some(Range(_, Some(_)))) => true
    case None                          => true
    case _                             => false
  }
}
