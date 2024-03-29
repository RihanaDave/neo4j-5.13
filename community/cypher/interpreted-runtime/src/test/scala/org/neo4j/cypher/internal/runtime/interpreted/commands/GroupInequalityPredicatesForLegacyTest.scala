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
package org.neo4j.cypher.internal.runtime.interpreted.commands

import org.neo4j.cypher.internal.runtime.interpreted.commands.LiteralHelper.literal
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Property
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Variable
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.AndedPropertyComparablePredicates
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.ComparablePredicate
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Equals
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.GreaterThan
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.GreaterThanOrEqual
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.LessThan
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.LessThanOrEqual
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.groupInequalityPredicatesForLegacy
import org.neo4j.cypher.internal.runtime.interpreted.commands.values.TokenType.PropertyKey
import org.neo4j.cypher.internal.util.NonEmptyList
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class GroupInequalityPredicatesForLegacyTest extends CypherFunSuite {

  val n_prop1: Property = Property(Variable("n"), PropertyKey("prop1"))
  val m_prop1: Property = Property(Variable("m"), PropertyKey("prop1"))
  val m_prop2: Property = Property(Variable("m"), PropertyKey("prop2"))

  test("Should handle single predicate") {
    groupInequalityPredicatesForLegacy(NonEmptyList(lessThan(n_prop1, 1))).toSet should equal(NonEmptyList(anded(
      n_prop1,
      lessThan(n_prop1, 1)
    )).toSet)
    groupInequalityPredicatesForLegacy(NonEmptyList(lessThanOrEqual(n_prop1, 1))).toSet should equal(NonEmptyList(anded(
      n_prop1,
      lessThanOrEqual(n_prop1, 1)
    )).toSet)
    groupInequalityPredicatesForLegacy(NonEmptyList(greaterThan(n_prop1, 1))).toSet should equal(NonEmptyList(anded(
      n_prop1,
      greaterThan(n_prop1, 1)
    )).toSet)
    groupInequalityPredicatesForLegacy(NonEmptyList(greaterThanOrEqual(n_prop1, 1))).toSet should equal(
      NonEmptyList(anded(n_prop1, greaterThanOrEqual(n_prop1, 1))).toSet
    )
  }

  test("Should group by lhs property") {
    groupInequalityPredicatesForLegacy(NonEmptyList(
      lessThan(n_prop1, 1),
      lessThanOrEqual(n_prop1, 2),
      lessThan(m_prop1, 3),
      greaterThan(m_prop1, 4),
      greaterThanOrEqual(m_prop2, 5)
    )).toSet should equal(NonEmptyList(
      anded(n_prop1, lessThan(n_prop1, 1), lessThanOrEqual(n_prop1, 2)),
      anded(m_prop1, lessThan(m_prop1, 3), greaterThan(m_prop1, 4)),
      anded(m_prop2, greaterThanOrEqual(m_prop2, 5))
    ).toSet)
  }

  test("Should keep other predicates when encountering both inequality and other predicates") {
    groupInequalityPredicatesForLegacy(NonEmptyList(
      lessThan(n_prop1, 1),
      equals(n_prop1, 1)
    )).toSet should equal(NonEmptyList(
      anded(n_prop1, lessThan(n_prop1, 1)),
      equals(n_prop1, 1)
    ).toSet)
  }

  test("Should keep other predicates when encountering only other predicates") {
    groupInequalityPredicatesForLegacy(NonEmptyList(
      equals(n_prop1, 1),
      equals(m_prop2, 2)
    )).toSet should equal(NonEmptyList(
      equals(n_prop1, 1),
      equals(m_prop2, 2)
    ).toSet)
  }

  test("Should not group inequalities on non-property lookups") {
    groupInequalityPredicatesForLegacy(NonEmptyList(
      lessThan(Variable("x"), 1),
      greaterThanOrEqual(Variable("x"), 1)
    )).toSet should equal(NonEmptyList(
      lessThan(Variable("x"), 1),
      greaterThanOrEqual(Variable("x"), 1)
    ).toSet)
  }

  private def equals(lhs: Expression, v: Int) =
    Equals(lhs, literal(v))

  private def lessThan(lhs: Expression, v: Int) =
    LessThan(lhs, literal(v))

  private def lessThanOrEqual(lhs: Expression, v: Int) =
    LessThanOrEqual(lhs, literal(v))

  private def greaterThan(lhs: Expression, v: Int) =
    GreaterThan(lhs, literal(v))

  private def greaterThanOrEqual(lhs: Expression, v: Int) =
    GreaterThanOrEqual(lhs, literal(v))

  private def anded(property: Property, first: ComparablePredicate, others: ComparablePredicate*) = {
    val variable = property.mapExpr.asInstanceOf[Variable]
    val inequalities = NonEmptyList(first, others: _*)
    AndedPropertyComparablePredicates(variable, property, inequalities)
  }
}
