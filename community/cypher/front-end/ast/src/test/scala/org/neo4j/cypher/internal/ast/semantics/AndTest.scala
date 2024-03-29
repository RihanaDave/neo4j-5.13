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
package org.neo4j.cypher.internal.ast.semantics

import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.util.DummyPosition
import org.neo4j.cypher.internal.util.symbols.CTBoolean
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTNode

class AndTest extends InfixExpressionTestBase(expressions.And(_, _)(DummyPosition(0))) {

  test("shouldCombineBooleans") {
    testValidTypes(CTBoolean, CTBoolean)(CTBoolean)
  }

  test("shouldCoerceArguments") {
    testInvalidApplication(CTInteger, CTBoolean)("Type mismatch: expected Boolean but was Integer")
    testInvalidApplication(CTBoolean, CTInteger)("Type mismatch: expected Boolean but was Integer")
  }

  test("shouldReturnErrorIfInvalidArgumentTypes") {
    testInvalidApplication(CTNode, CTBoolean)("Type mismatch: expected Boolean but was Node")
    testInvalidApplication(CTBoolean, CTNode)("Type mismatch: expected Boolean but was Node")
  }
}
