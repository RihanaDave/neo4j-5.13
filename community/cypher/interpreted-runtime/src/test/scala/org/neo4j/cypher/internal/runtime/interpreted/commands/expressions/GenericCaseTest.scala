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
package org.neo4j.cypher.internal.runtime.interpreted.commands.expressions

import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.runtime.interpreted.commands.LiteralHelper.literal
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.CoercedPredicate
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Equals
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Predicate
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.values.storable.Values.NO_VALUE
import org.neo4j.values.storable.Values.stringValue

class GenericCaseTest extends CypherFunSuite {

  test("case_with_single_alternative_works") {
    // GIVEN
    val caseExpr = case_(
      (1, 1) -> "one"
    )

    // WHEN
    val result = caseExpr(CypherRow.empty, QueryStateHelper.empty)

    // THEN
    result should equal(stringValue("one"))
  }

  test("case_with_two_alternatives_picks_the_second") {
    // GIVEN
    val caseExpr = case_(
      (1, 2) -> "one",
      (2, 2) -> "two"
    )

    // WHEN
    val result = caseExpr(CypherRow.empty, QueryStateHelper.empty)

    // THEN
    result should equal(stringValue("two"))
  }

  test("case_with_no_match_returns_null") {
    // GIVEN
    val caseExpr = case_(
      (1, 2) -> "one",
      (2, 3) -> "two"
    )

    // WHEN
    val result = caseExpr(CypherRow.empty, QueryStateHelper.empty)

    // THEN
    result should equal(NO_VALUE)
  }

  test("case_with_no_match_returns_default") {
    // GIVEN
    val caseExpr = case_(
      (1, 2) -> "one",
      (2, 3) -> "two"
    ) defaultsTo "other"

    // WHEN
    val result = caseExpr(CypherRow.empty, QueryStateHelper.empty)

    // THEN
    result should equal(stringValue("other"))
  }

  test("case_with_a_single_null_value_uses_the_default") {
    // GIVEN CASE WHEN null THEN 42 ELSE "defaults"
    val caseExpr = GenericCase(IndexedSeq(CoercedPredicate(Null()) -> literal(42)), Some(literal("defaults")))

    // WHEN
    val result = caseExpr(CypherRow.empty, QueryStateHelper.empty)

    // THEN
    assert(result === stringValue("defaults"))
  }

  private def case_(alternatives: ((Any, Any), Any)*): GenericCase = {
    val mappedAlt: IndexedSeq[(Predicate, Expression)] = alternatives.toIndexedSeq.map {
      case ((a, b), c) => (Equals(literal(a), literal(b)), literal(c))
    }

    GenericCase(mappedAlt, None)
  }

  implicit class SimpleCasePimp(in: GenericCase) {
    def defaultsTo(a: Any): GenericCase = GenericCase(in.alternatives, Some(literal(a)))
  }
}
