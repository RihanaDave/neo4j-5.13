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
package org.neo4j.cypher.internal.plandescription

import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.Namespace
import org.neo4j.cypher.internal.expressions.SymbolicName
import org.neo4j.cypher.internal.ir.ordering.ProvidedOrder
import org.neo4j.cypher.internal.plandescription.Arguments.Order
import org.neo4j.cypher.internal.util.helpers.LineBreakRemover.removeLineBreaks
import org.neo4j.cypher.internal.util.helpers.NameDeduplicator.removeGeneratedNamesAndParams

/**
 * This should be the only place creating [[PrettyString]]s directly.
 * Given that they live in different modules, we cannot make this the companion object.
 */
object asPrettyString {
  private val stringifier: ExpressionStringifier = ExpressionStringifier.pretty(e => e.asCanonicalStringVal)

  /**
   * This will create a PrettyString without any modifications to the given string.
   * Use only when you know that the given string has no autogenerated names and backticks already in the right places,
   * or from test code.
   */
  def raw(s: String): PrettyString = PrettyString(s)

  /**
   * Remove autogenerated names/params and add backticks.
   */
  def apply(s: SymbolicName): PrettyString = PrettyString(if (s == null) {
    "null"
  } else {
    stringifier(s)
  })

  /**
   * Remove autogenerated names/params and add backticks.
   */
  def apply(n: Namespace): PrettyString = PrettyString(if (n == null) {
    "null"
  } else {
    stringifier(n)
  })

  /**
   * Remove autogenerated names/params and add backticks.
   */
  def apply(expr: Expression): PrettyString = PrettyString(if (expr == null) {
    "null"
  } else {
    stringifier(expr)
  })

  /**
   * Remove autogenerated names/params and add backticks.
   */
  def apply(variableName: String): PrettyString = PrettyString(ExpressionStringifier.backtick(makePretty(variableName)))

  /**
   * Create an Order Argument from a ProvidedOrder. Remove autogenerated names and add backticks.
   */
  def order(order: ProvidedOrder): Order = Order(PrettyString(serializeProvidedOrder(order)))

  private def serializeProvidedOrder(providedOrder: ProvidedOrder): String = {
    providedOrder.columns.map(col => {
      val direction = if (col.isAscending) "ASC" else "DESC"
      s"${apply(col.expression)} $direction"
    }).mkString(", ")
  }

  private def makePretty(s: String): String =
    removeGeneratedNamesAndParams(removeLineBreaks(s))

  /**
   * Used to create PrettyStrings with interpolations or literal PrettyStrings, e.g.
   * {{{pretty"foo$bar"}}} or {{{pretty"literal"}}}
   */
  implicit class PrettyStringInterpolator(val sc: StringContext) extends AnyVal {

    def pretty(args: PrettyString*): PrettyString = {
      val connectors = sc.parts.iterator
      val expressions = args.iterator
      val buf = new StringBuffer(connectors.next())
      while (connectors.hasNext) {
        buf append expressions.next().prettifiedString
        buf append connectors.next()
      }
      PrettyString(buf.toString)
    }
  }

  /**
   * Provides the method [[mkPrettyString]] for TraversableOnce[PrettyString]
   */
  implicit class PrettyStringMaker(iterableOnce: IterableOnce[PrettyString]) {
    def mkPrettyString(sep: String): PrettyString = PrettyString(iterableOnce.iterator.mkString(sep))
    def mkPrettyString: PrettyString = PrettyString(iterableOnce.iterator.mkString)

    def mkPrettyString(start: String, sep: String, end: String): PrettyString =
      PrettyString(iterableOnce.iterator.mkString(start, sep, end))
  }
}