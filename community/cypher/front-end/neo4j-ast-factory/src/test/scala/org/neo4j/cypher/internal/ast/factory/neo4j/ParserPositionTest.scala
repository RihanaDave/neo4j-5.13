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

import org.neo4j.cypher.internal.ast.ExistsExpression
import org.neo4j.cypher.internal.ast.LoadCSV
import org.neo4j.cypher.internal.ast.RemovePropertyItem
import org.neo4j.cypher.internal.ast.SetExactPropertiesFromMapItem
import org.neo4j.cypher.internal.ast.SetIncludingPropertiesFromMapItem
import org.neo4j.cypher.internal.ast.SetPropertyItem
import org.neo4j.cypher.internal.ast.ShowDatabase
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.UseGraph
import org.neo4j.cypher.internal.ast.Yield
import org.neo4j.cypher.internal.expressions.ContainerIndex
import org.neo4j.cypher.internal.expressions.ListSlice
import org.neo4j.cypher.internal.expressions.NonPrefixedPatternPart
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.label_expressions.LabelExpressionPredicate
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.OpenCypherExceptionFactory
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.cypher.internal.util.test_helpers.TestName

import scala.util.Try

class ParserPositionTest extends CypherFunSuite with TestName {
  private val exceptionFactory = new OpenCypherExceptionFactory(None)

  private val javaCcAST: String => Try[Statement] =
    (query: String) => Try(JavaCCParser.parse(query, exceptionFactory))

  test("MATCH (n) RETURN n.prop") {
    validatePosition(testName, _.isInstanceOf[Property], InputPosition(17, 1, 18))
  }

  test("MATCH (n) SET n.prop = 1") {
    validatePosition(testName, _.isInstanceOf[SetPropertyItem], InputPosition(14, 1, 15))
  }

  test("MATCH (n) REMOVE n.prop") {
    validatePosition(testName, _.isInstanceOf[RemovePropertyItem], InputPosition(17, 1, 18))
  }

  test("LOAD CSV FROM 'url' AS line") {
    validatePosition(testName, _.isInstanceOf[LoadCSV], InputPosition(0, 1, 1))
  }

  test("USE GRAPH(x) RETURN 1 as y ") {
    validatePosition(testName, _.isInstanceOf[UseGraph], InputPosition(0, 1, 1))
  }

  test("CREATE (a)-[:X]->(b)") {
    validatePosition(testName, _.isInstanceOf[NonPrefixedPatternPart], InputPosition(7, 1, 8))
  }

  test("SHOW ALL ROLES YIELD role") {
    validatePosition(testName, _.isInstanceOf[Yield], InputPosition(15, 1, 16))
  }

  test("RETURN 3 IN list[0] AS r") {
    validatePosition(testName, _.isInstanceOf[ContainerIndex], InputPosition(17, 1, 18))
  }

  test("RETURN 3 IN [1, 2, 3][0..1] AS r") {
    validatePosition(testName, _.isInstanceOf[ListSlice], InputPosition(21, 1, 22))
  }

  test("MATCH (a) WHERE NOT (a:A)") {
    validatePosition(testName, _.isInstanceOf[LabelExpressionPredicate], InputPosition(21, 1, 22))
  }

  test("MATCH (n) WHERE exists { (n) --> () }") {
    val exists = javaCcAST(testName).folder.treeFindByClass[ExistsExpression].get
    exists.position shouldBe InputPosition(16, 1, 17)
    exists.folder.treeFindByClass[Pattern].get.position shouldBe InputPosition(25, 1, 26)
  }

  test("MATCH (n) WHERE exists { MATCH (n)-[r]->(m) }") {
    val exists = javaCcAST(testName).folder.treeFindByClass[ExistsExpression].get
    exists.position shouldBe InputPosition(16, 1, 17)
    exists.folder.treeFindByClass[Pattern].get.position shouldBe InputPosition(31, 1, 32)
  }

  test("MATCH (n) WHERE exists { MATCH (m) WHERE exists { (n)-[]->(m) } }") {
    val existsExpressions = javaCcAST(testName).folder.findAllByClass[ExistsExpression]
    existsExpressions match {
      case Seq(exists, existsNested) =>
        exists.position shouldBe InputPosition(16, 1, 17)
        exists.folder.treeFindByClass[Pattern].get.position shouldBe InputPosition(31, 1, 32)
        existsNested.position shouldBe InputPosition(41, 1, 42)
        existsNested.folder.treeFindByClass[Pattern].get.position shouldBe InputPosition(50, 1, 51)
      case _ => fail("Expected existsExpressions to be a Seq of length 2")
    }
  }

  test("MATCH (n) SET n += {name: null}") {
    validatePosition(testName, _.isInstanceOf[SetIncludingPropertiesFromMapItem], InputPosition(14, 1, 15))
  }

  test("MATCH (n) SET n = {name: null}") {
    validatePosition(testName, _.isInstanceOf[SetExactPropertiesFromMapItem], InputPosition(14, 1, 15))
  }

  Seq(
    ("DATABASES YIELD name", 21),
    ("DEFAULT DATABASE YIELD name", 28),
    ("HOME DATABASE YIELD name", 25),
    ("DATABASE $db YIELD name", 24),
    ("DATABASE neo4j YIELD name", 26)
  ).foreach { case (name, variableOffset) =>
    test(s"SHOW $name") {
      validatePosition(testName, _.isInstanceOf[ShowDatabase], InputPosition(0, 1, 1))
      validatePosition(testName, _.isInstanceOf[Variable], InputPosition(variableOffset, 1, variableOffset + 1))
    }
  }

  test("DROP INDEX ON :Person(name)") {
    validatePosition(testName, _.isInstanceOf[PropertyKeyName], InputPosition(22, 1, 23))
  }

  private def validatePosition(query: String, astToVerify: ASTNode => Boolean, pos: InputPosition): Unit = {
    val propAst = javaCcAST(query).folder.treeFind[ASTNode] {
      case ast if astToVerify(ast) => true
    }

    propAst.get.position shouldBe pos
  }
}
