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
package org.neo4j.cypher.internal.ir.ast

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.RegularSinglePlannerQuery
import org.neo4j.cypher.internal.ir.Selections
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class IRExpressionTest extends CypherFunSuite with AstConstructionTestSupport {

  test("ListIRExpression should return the correct dependencies") {
    val e = ListIRExpression(
      RegularSinglePlannerQuery(
        QueryGraph(
          argumentIds = Set("a"),
          patternNodes = Set("a", "b"),
          // This is to make sure that the appearance of varFor("b") does not add b as a dependency
          selections = Selections.from(varFor("b"))
        )
      ),
      "anon_0",
      "anon_1",
      "ListIRExpression"
    )(pos, Some(Set(varFor("b"))), Some(Set(varFor("a"))))

    e.dependencies should equal(Set(varFor("a")))
  }

  test("ExistsIRExpression should return the correct dependencies") {
    val e = ExistsIRExpression(
      RegularSinglePlannerQuery(
        QueryGraph(
          argumentIds = Set("a"),
          patternNodes = Set("a", "b"),
          // This is to make sure that the appearance of varFor("b") does not add b as a dependency
          selections = Selections.from(varFor("b"))
        )
      ),
      "anon_0",
      "ExistsIRExpression"
    )(pos, Some(Set(varFor("b"))), Some(Set(varFor("a"))))

    e.dependencies should equal(Set(varFor("a")))
  }

  test("ListIRExpression contained in another expression should return the correct dependencies") {
    val e = listOf(ListIRExpression(
      RegularSinglePlannerQuery(
        QueryGraph(
          argumentIds = Set("a"),
          patternNodes = Set("a", "b"),
          // This is to make sure that the appearance of varFor("b") does not add b as a dependency
          selections = Selections.from(varFor("b"))
        )
      ),
      "anon_0",
      "anon_1",
      "ListIRExpression"
    )(pos, Some(Set(varFor("b"))), Some(Set(varFor("a")))))

    e.dependencies should equal(Set(varFor("a")))
  }
}
