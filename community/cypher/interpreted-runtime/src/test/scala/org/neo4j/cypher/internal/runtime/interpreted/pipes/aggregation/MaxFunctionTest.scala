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
package org.neo4j.cypher.internal.runtime.interpreted.pipes.aggregation

import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.values.storable.DoubleValue
import org.neo4j.values.storable.IntValue
import org.neo4j.values.storable.Values
import org.neo4j.values.storable.Values.doubleValue
import org.neo4j.values.storable.Values.intValue
import org.neo4j.values.storable.Values.stringValue

class MaxFunctionTest extends CypherFunSuite with AggregateTest {
  def createAggregator(inner: Expression) = new MaxFunction(inner)

  test("singleValueReturnsThatNumber") {
    val result = aggregateOn(intValue(1))

    result should equal(intValue(1))
    result shouldBe an[IntValue]
  }

  test("singleValueOfDecimalReturnsDecimal") {
    val result = aggregateOn(doubleValue(1.0d))

    result should equal(doubleValue(1.0))
    result shouldBe a[DoubleValue]
  }

  test("mixOfIntAndDoubleYieldsDouble") {
    val result = aggregateOn(intValue(1), doubleValue(2.0d))

    result should equal(doubleValue(2.0))
    result shouldBe a[DoubleValue]
  }

  test("nullDoesNotChangeTheSum") {
    val result = aggregateOn(intValue(1), Values.NO_VALUE)

    result should equal(intValue(1))
    result shouldBe a[IntValue]
  }

  test("mixed numbers and strings works fine") {
    val result = aggregateOn(intValue(1), stringValue("wut"))

    result shouldBe intValue(1)
  }

  test("aggregating strings work") {
    val result =
      aggregateOn(stringValue("abc"), stringValue("a"), stringValue("b"), stringValue("B"), stringValue("abc1"))

    result should equal(stringValue("b"))
  }

  test("nulls are simply skipped") {
    val result =
      aggregateOn(stringValue("abc"), stringValue("a"), Values.NO_VALUE, stringValue("B"), stringValue("abc1"))

    result should equal(stringValue("abc1"))
  }
}
