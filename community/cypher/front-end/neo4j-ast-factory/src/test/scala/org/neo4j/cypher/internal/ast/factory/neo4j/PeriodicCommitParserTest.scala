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
package org.neo4j.cypher.internal.ast.factory.neo4j

import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.cst.factory.neo4j.AntlrRule
import org.neo4j.cypher.internal.cst.factory.neo4j.Cst

class PeriodicCommitParserTest extends ParserSyntaxTreeBase[Cst.Statement, Statement] {

  implicit private val javaccRule: JavaccRule[Statement] = JavaccRule.Statement
  implicit private val antlrRule: AntlrRule[Cst.Statement] = AntlrRule.Statement

  val message =
    "The PERIODIC COMMIT query hint is no longer supported. Please use CALL { ... } IN TRANSACTIONS instead. (line 1, column 7 (offset: 6))"

  test("USING PERIODIC COMMIT LOAD CSV FROM 'foo' AS l RETURN l") {
    assertFailsWithMessage(testName, message)
  }

  test("USING PERIODIC COMMIT 200 LOAD CSV FROM 'foo' AS l RETURN l") {
    assertFailsWithMessage(testName, message)
  }

  test("USING PERIODIC COMMIT RETURN 1") {
    assertFailsWithMessage(testName, message)
  }
}
