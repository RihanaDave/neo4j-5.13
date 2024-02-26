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

import org.neo4j.cypher.internal.ast
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.LoadCSV
import org.neo4j.cypher.internal.cst.factory.neo4j.AntlrRule
import org.neo4j.cypher.internal.cst.factory.neo4j.Cst

class LoadCsvParserTest extends ParserSyntaxTreeBase[Cst.Clause, ast.Clause] {
  implicit val javaccRule: JavaccRule[Clause] = JavaccRule.Clause
  implicit val antlrRule: AntlrRule[Cst.Clause] = AntlrRule.Clause

  private val fileExpressionFailed =
    "Failed to parse the file expression. Please remember to use quotes for string literals."

  test("LOAD CSV WITH HEADERS FROM 'file:///ALL_PLANT_RMs_2.csv' AS l") {
    yields(LoadCSV(withHeaders = true, literalString("file:///ALL_PLANT_RMs_2.csv"), varFor("l"), None))
  }

  test("""LOAD CSV WITH HEADERS FROM "file:///ALL_PLANT_RMs_2.csv" AS l""") {
    yields(LoadCSV(withHeaders = true, literalString("file:///ALL_PLANT_RMs_2.csv"), varFor("l"), None))
  }

  test("LOAD CSV WITH HEADERS FROM `var` AS l") {
    yields(LoadCSV(withHeaders = true, varFor("var"), varFor("l"), None))
  }

  test("LOAD CSV WITH HEADERS FROM '1' + '2' AS l") {
    yields(LoadCSV(withHeaders = true, add(literalString("1"), literalString("2")), varFor("l"), None))
  }

  test("LOAD CSV WITH HEADERS FROM 1+2 AS l") {
    yields(LoadCSV(withHeaders = true, add(literalInt(1), literalInt(2)), varFor("l"), None))
  }

  test("LOAD CSV WITH HEADERS FROM file:///ALL_PLANT_RMs_2.csv AS l") {
    assertFailsWithMessageStart(testName, fileExpressionFailed)
  }

  test("LOAD CSV WITH HEADERS FROM 'file:///ALL_PLANT_RMs_2.csv AS l") {
    assertFailsWithMessageStart(testName, fileExpressionFailed)
  }

  test("""LOAD CSV WITH HEADERS FROM "file:///ALL_PLANT_RMs_2.csv AS l""") {
    assertFailsWithMessageStart(testName, fileExpressionFailed)
  }

  test("LOAD CSV WITH HEADERS FROM `var AS l") {
    assertFailsWithMessageStart(testName, fileExpressionFailed)
  }
}
