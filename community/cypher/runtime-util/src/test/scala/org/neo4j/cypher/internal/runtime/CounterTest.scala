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
package org.neo4j.cypher.internal.runtime

import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class CounterTest extends CypherFunSuite {

  test("counts up") {
    (Counter(0) += 1) should equal(1L)
    (Counter(0) += 3) should equal(3L)
    (Counter(7) += 1) should equal(8L)
  }

  test("counts down") {
    (Counter(7) -= 1) should equal(6L)
  }

  test("resets counter") {
    Counter(10).reset(5).counted should equal(5)
  }

  test("streams all values") {
    Counter().values.take(5).toList should equal(List(1L, 2L, 3L, 4L, 5L))
  }

  test("maps all values") {
    Counter().map(_ * 2).take(3).toList should equal(List(2L, 4L, 6L))
  }

  test("tracks iterators") {
    // given
    val iterator = Counter(10).track(1.to(5).iterator)

    // when
    iterator.toList

    // then
    iterator.counted should equal(15L)
  }

  test("limits tracked iterators") {
    an[Exception] should be thrownBy {
      Counter().track(1.to(5).iterator).limit(2) { counted => counted shouldBe 3; fail("Limit reached") }.toList
    }
  }
}
