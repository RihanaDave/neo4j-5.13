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

import org.mockito.Mockito.when
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.ImplicitValueConversion.toListValue
import org.neo4j.cypher.internal.runtime.ImplicitValueConversion.toPathValue
import org.neo4j.cypher.internal.runtime.ImplicitValueConversion.toStringValue
import org.neo4j.cypher.internal.runtime.PathImpl
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.CharacterLengthFunction
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Variable
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.exceptions.CypherTypeException
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Relationship
import org.neo4j.values.storable.Values
import org.neo4j.values.storable.Values.longValue

class CharacterFunctionTest extends CypherFunSuite {

  test("character length can be used on strings") {
    // given
    val s = "it was the"
    val m = CypherRow.from("s" -> s)
    val characterLengthFunction = CharacterLengthFunction(Variable("s"))

    // when
    val result = characterLengthFunction.apply(m, QueryStateHelper.empty)

    // then
    result should equal(longValue(10))
  }

  test("character length cannot be used on collections") {
    // given
    val l = Seq("it", "was", "the")
    val m = CypherRow.from("l" -> l)
    val characterLengthFunction = CharacterLengthFunction(Variable("l"))

    // when/then
    val e = intercept[CypherTypeException](characterLengthFunction.apply(m, QueryStateHelper.empty))
    e.getMessage should be(
      "Invalid input for function 'character_length()': Expected a String, got: List{String(\"it\"), String(\"was\"), String(\"the\")}"
    )
  }

  test("character length cannot be used on paths") {
    // given
    val p = PathImpl(mockNode(), mock[Relationship], mockNode())
    val m = CypherRow.from("p" -> p)
    val characterLengthFunction = CharacterLengthFunction(Variable("p"))

    // when/then
    val e = intercept[CypherTypeException](characterLengthFunction.apply(m, QueryStateHelper.empty))
    e.getMessage should be("Invalid input for function 'character_length()': Expected a String, got: Path{(0)-[0]-(0)}")
  }

  test("character length cannot be used on integers") {
    // given
    val m = CypherRow.from("p" -> Values.of(33))
    val characterLengthFunction = CharacterLengthFunction(Variable("p"))

    // when/then
    val e = intercept[CypherTypeException](characterLengthFunction.apply(m, QueryStateHelper.empty))
    e.getMessage should be("Invalid input for function 'character_length()': Expected a String, got: Int(33)")
  }

  private def mockNode() = {
    val node = mock[Node]
    when(node.getElementId).thenReturn("dummy")
    node
  }
}
