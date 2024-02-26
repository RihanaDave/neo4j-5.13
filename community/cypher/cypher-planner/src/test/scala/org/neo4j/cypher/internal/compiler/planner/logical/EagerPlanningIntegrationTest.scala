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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.configuration.GraphDatabaseInternalSettings
import org.neo4j.configuration.GraphDatabaseInternalSettings.EagerAnalysisImplementation
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.assertIsNode
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.labelName
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.literalInt
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.relTypeName
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.varFor
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.compiler.helpers.LogicalPlanBuilder
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.expressions.HasDegreeGreaterThan
import org.neo4j.cypher.internal.expressions.SemanticDirection.BOTH
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.ir.EagernessReason
import org.neo4j.cypher.internal.ir.EagernessReason.Conflict
import org.neo4j.cypher.internal.ir.EagernessReason.LabelReadSetConflict
import org.neo4j.cypher.internal.ir.EagernessReason.ReadDeleteConflict
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNode
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createRelationship
import org.neo4j.cypher.internal.logical.builder.TestNFABuilder
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.logical.plans.NFA
import org.neo4j.cypher.internal.logical.plans.StatefulShortestPath
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

import scala.collection.immutable.ListSet

class EagerPlanningIntegrationTest extends CypherFunSuite with LogicalPlanningIntegrationTestSupport {

  test("MATCH (n)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .build()

    val query = "MATCH (n)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.argument("n")
        .allNodeScan("n")
        .build()
    )
  }

  test("MATCH (n:N)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .build()

    val query = "MATCH (n:N)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.argument("n")
        .nodeByLabelScan("n", "N")
        .build()
    )
  }

  test("MATCH (n:N {prop: 42})  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .build()

    val query = "MATCH (n:N {prop: 42})  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.argument("n")
        .filter("n.prop = 42")
        .nodeByLabelScan("n", "N")
        .build()
    )
  }

  test("MATCH (n:N) WHERE n.prop > 23  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .build()

    val query = "MATCH (n:N) WHERE n.prop > 23  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.argument("n")
        .filter("n.prop > 23")
        .nodeByLabelScan("n", "N")
        .build()
    )
  }

  test("MATCH (n)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS with index") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .addNodeIndex("N", Seq("prop"), 1, 0.01)
      .build()

    val query = "MATCH (n)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.argument("n")
        .allNodeScan("n")
        .build()
    )
  }

  test("MATCH (n:N)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS with index") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .addNodeIndex("N", Seq("prop"), 1, 0.01)
      .build()

    val query = "MATCH (n:N)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.argument("n")
        .nodeByLabelScan("n", "N")
        .build()
    )
  }

  test("MATCH (n:N {prop: 42})  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS with index") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .addNodeIndex("N", Seq("prop"), 1, 0.01)
      .build()

    val query = "MATCH (n:N {prop: 42})  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.argument("n")
        .nodeIndexOperator("n:N(prop = 42)")
        .build()
    )
  }

  test("MATCH (n:N) WHERE n.prop > 23  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS with index") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .addNodeIndex("N", Seq("prop"), 1, 0.01)
      .build()

    val query = "MATCH (n:N) WHERE n.prop > 23  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.argument("n")
        .nodeIndexOperator("n:N(prop > 23)")
        .build()
    )
  }

  test("MATCH (x) WITH x, 1 as dummy  MATCH (n)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .build()

    val query = "MATCH (x) WITH x, 1 as dummy  MATCH (n)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .|.argument("n")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .apply()
        .|.allNodeScan("n", "x", "dummy")
        .projection("1 AS dummy")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("x")))
        .allNodeScan("x")
        .build()
    )
  }

  test("MATCH (x) WITH x, 1 as dummy  MATCH (n:N)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .build()

    val query = "MATCH (x) WITH x, 1 as dummy  MATCH (n:N)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .|.argument("n")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .apply()
        .|.nodeByLabelScan("n", "N", IndexOrderNone, "x", "dummy")
        .projection("1 AS dummy")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("x")))
        .allNodeScan("x")
        .build()
    )
  }

  test("MATCH (x) WITH x, 1 as dummy  MATCH (n:N {prop: 42})  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .build()

    val query = "MATCH (x) WITH x, 1 as dummy  MATCH (n:N {prop: 42})  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .|.argument("n")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .filter("n.prop = 42")
        .apply()
        .|.nodeByLabelScan("n", "N", IndexOrderNone, "x", "dummy")
        .projection("1 AS dummy")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("x")))
        .allNodeScan("x")
        .build()
    )
  }

  test("MATCH (x) WITH x, 1 as dummy  MATCH (n:N) WHERE n.prop > 23  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .build()

    val query =
      "MATCH (x) WITH x, 1 as dummy  MATCH (n:N) WHERE n.prop > 23  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .|.argument("n")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .filter("n.prop > 23")
        .apply()
        .|.nodeByLabelScan("n", "N", IndexOrderNone, "x", "dummy")
        .projection("1 AS dummy")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("x")))
        .allNodeScan("x")
        .build()
    )
  }

  test("MATCH (x) WITH x, 1 as dummy  MATCH (n)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS with index") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .addNodeIndex("N", Seq("prop"), 1, 0.01)
      .build()

    val query = "MATCH (x) WITH x, 1 as dummy  MATCH (n)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .|.argument("n")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .apply()
        .|.allNodeScan("n", "x", "dummy")
        .projection("1 AS dummy")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("x")))
        .allNodeScan("x")
        .build()
    )
  }

  test("MATCH (x) WITH x, 1 as dummy  MATCH (n:N)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS with index") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .addNodeIndex("N", Seq("prop"), 1, 0.01)
      .build()

    val query = "MATCH (x) WITH x, 1 as dummy  MATCH (n:N)  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .|.argument("n")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .apply()
        .|.nodeByLabelScan("n", "N", IndexOrderNone, "x", "dummy")
        .projection("1 AS dummy")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("x")))
        .allNodeScan("x")
        .build()
    )
  }

  test(
    "MATCH (x) WITH x, 1 as dummy  MATCH (n:N {prop: 42})  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS with index"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .addNodeIndex("N", Seq("prop"), 1, 0.01)
      .build()

    val query = "MATCH (x) WITH x, 1 as dummy  MATCH (n:N {prop: 42})  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .|.argument("n")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .apply()
        .|.nodeIndexOperator("n:N(prop = 42)", argumentIds = Set("x", "dummy"))
        .projection("1 AS dummy")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("x")))
        .allNodeScan("x")
        .build()
    )
  }

  test(
    "MATCH (x) WITH x, 1 as dummy  MATCH (n:N) WHERE n.prop > 23  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS with index"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("N", 50)
      .addNodeIndex("N", Seq("prop"), 1, 0.01)
      .build()

    val query =
      "MATCH (x) WITH x, 1 as dummy  MATCH (n:N) WHERE n.prop > 23  CALL { WITH n DETACH DELETE n } IN TRANSACTIONS"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .transactionForeach(1000)
        .|.detachDeleteNode("n")
        .|.eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .|.argument("n")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("n")))
        .apply()
        .|.nodeIndexOperator("n:N(prop > 23)", argumentIds = Set("x", "dummy"))
        .projection("1 AS dummy")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("x")))
        .allNodeScan("x")
        .build()
    )
  }

  test(
    "MATCH (a:A), (c:C) MERGE (a)-[:BAR]->(b:B) WITH c MATCH (c) WHERE (c)-[:BAR]->() RETURN count(*)"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 50)
      .setLabelCardinality("C", 50)
      .setLabelCardinality("B", 50)
      .setRelationshipCardinality("()-[:BAR]->()", 10)
      .setRelationshipCardinality("(:A)-[:BAR]->()", 10)
      .setRelationshipCardinality("(:A)-[:BAR]->(:B)", 10)
      .build()

    val query =
      "MATCH (a:A), (c:C) MERGE (a)-[:BAR]->(b:B) WITH c MATCH (c) WHERE (c)-[:BAR]->() RETURN count(*)"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults("`count(*)`")
        .aggregation(Seq(), Seq("count(*) AS `count(*)`"))
        .filterExpression(
          HasDegreeGreaterThan(varFor("c"), Some(relTypeName("BAR")), OUTGOING, literalInt(0))(InputPosition.NONE),
          assertIsNode("c")
        )
        .eager(ListSet(EagernessReason.Unknown))
        .apply()
        .|.merge(
          Seq(createNode("b", "B")),
          Seq(createRelationship("anon_0", "a", "BAR", "b", OUTGOING)),
          lockNodes = Set("a")
        )
        .|.filter("b:B")
        .|.expandAll("(a)-[anon_0:BAR]->(b)")
        .|.argument("a")
        .cartesianProduct()
        .|.nodeByLabelScan("c", "C")
        .nodeByLabelScan("a", "A")
        .build()
    )
  }

  test(
    "MATCH (a:A), (c:C) MERGE (a)-[:BAR]->(b:B) WITH c MATCH (c) WHERE (c)-[]->() RETURN count(*)"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(100)
      .setLabelCardinality("A", 50)
      .setLabelCardinality("C", 50)
      .setLabelCardinality("B", 50)
      .setRelationshipCardinality("()-[:BAR]->()", 10)
      .setRelationshipCardinality("(:A)-[:BAR]->()", 10)
      .setRelationshipCardinality("(:A)-[:BAR]->(:B)", 10)
      .build()

    val query =
      "MATCH (a:A), (c:C) MERGE (a)-[:BAR]->(b:B) WITH c MATCH (c) WHERE (c)-[]->() RETURN count(*)"

    val plan = planner.plan(query)

    plan should equal(
      planner.planBuilder()
        .produceResults("`count(*)`")
        .aggregation(Seq(), Seq("count(*) AS `count(*)`"))
        .filterExpression(
          HasDegreeGreaterThan(varFor("c"), None, OUTGOING, literalInt(0))(InputPosition.NONE),
          assertIsNode("c")
        )
        .eager(ListSet(EagernessReason.Unknown))
        .apply()
        .|.merge(
          Seq(createNode("b", "B")),
          Seq(createRelationship("anon_0", "a", "BAR", "b", OUTGOING)),
          lockNodes = Set("a")
        )
        .|.filter("b:B")
        .|.expandAll("(a)-[anon_0:BAR]->(b)")
        .|.argument("a")
        .cartesianProduct()
        .|.nodeByLabelScan("c", "C")
        .nodeByLabelScan("a", "A")
        .build()
    )
  }

  test("should plan Eager with IR eagerness") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setLabelCardinality("A", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .build()

    val query = "UNWIND [1, 2] AS i MATCH (a:A) CREATE (a2:A)"

    val plan = planner.plan(query)
    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .create(createNode("a2", "A"))
        .eager(ListSet(EagernessReason.Unknown))
        .apply()
        .|.nodeByLabelScan("a", "A", IndexOrderNone, "i")
        .unwind("[1, 2] AS i")
        .argument()
        .build()
    )
  }

  test("should plan Eager with LP eagerness") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setLabelCardinality("A", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.LP)
      .build()

    val query = "UNWIND [1, 2] AS i MATCH (a:A) CREATE (a2:A)"

    val plan = planner.plan(query)
    plan should equal(
      planner.planBuilder()
        .produceResults()
        .emptyResult()
        .create(createNode("a2", "A"))
        .eager(ListSet(LabelReadSetConflict(labelName("A")).withConflict(Conflict(Id(2), Id(5)))))
        .apply()
        .|.nodeByLabelScan("a", "A", IndexOrderNone, "i")
        .unwind("[1, 2] AS i")
        .argument()
        .build()
    )
  }

  // SHORTEST Tests

  case class ShortestPathParameters(
    start: String,
    end: String,
    query: String,
    singletonNodeVariables: Set[String],
    singletonRelationshipVariables: Set[String],
    selector: StatefulShortestPath.Selector,
    nfa: NFA
  )

  def statefulShortestPath(planBuilder: LogicalPlanBuilder, parameters: ShortestPathParameters): LogicalPlanBuilder =
    planBuilder
      .statefulShortestPath(
        parameters.start,
        parameters.end,
        parameters.query,
        None,
        Set.empty,
        Set.empty,
        parameters.singletonNodeVariables,
        parameters.singletonRelationshipVariables,
        StatefulShortestPath.Selector.Shortest(1),
        parameters.nfa,
        false
      )

  val `(start)-[r]->(end)` : ShortestPathParameters =
    ShortestPathParameters(
      "start",
      "end",
      "SHORTEST 1 ((start)-[r]->(end))",
      Set("end"),
      Set("r"),
      StatefulShortestPath.Selector.Shortest(1),
      new TestNFABuilder(0, "start")
        .addTransition(0, 1, "(start)-[r]->(end)")
        .addFinalState(1)
        .build()
    )

  val `((start)((a)-[r:R]->(b))+(end))` : ShortestPathParameters =
    ShortestPathParameters(
      "start",
      "end",
      "SHORTEST 1 ((start) ((a)-[r:R]->(b)){1, } (end) WHERE unique(`r`))",
      Set("end"),
      Set.empty,
      StatefulShortestPath.Selector.Shortest(1),
      new TestNFABuilder(0, "start")
        .addTransition(0, 1, "(start) (a)")
        .addTransition(1, 2, "(a)-[r:R]->(b)")
        .addTransition(2, 1, "(b) (a)")
        .addTransition(2, 3, "(b) (end)")
        .addFinalState(3)
        .build()
    )

  val `(start)-[r:R]->(end)` : ShortestPathParameters =
    ShortestPathParameters(
      "start",
      "end",
      "SHORTEST 1 ((start)-[r:R]->(end))",
      Set("end"),
      Set("r"),
      StatefulShortestPath.Selector.Shortest(1),
      new TestNFABuilder(0, "start")
        .addTransition(0, 1, "(start)-[r:R]->(end)")
        .addFinalState(1)
        .build()
    )

  test("Shortest match produces an eager when there is a relationship overlap") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query = "MATCH ANY SHORTEST (start)-[r]->(end) CREATE (end)-[s:S]->() RETURN end"
    val plan = planner.plan(query)

    val topPlan = planner.planBuilder()
      .produceResults("end")
      .create(createNode("anon_0"), createRelationship("s", "end", "S", "anon_0", OUTGOING))
      .eager(ListSet(EagernessReason.Unknown))

    val expectedPlan = statefulShortestPath(topPlan, `(start)-[r]->(end)`)
      .allNodeScan("start")
      .build()

    plan should equal(expectedPlan)
  }

  test("Shortest match should not produce an eager when there is no relationship overlap") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[]->()", 10)
      .setRelationshipCardinality("()-[:R]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query = "MATCH ANY SHORTEST (start)-[r:R]->(end) CREATE (end)-[s:S]->() RETURN end"
    val plan = planner.plan(query)

    val topPlan = planner.planBuilder()
      .produceResults("end")
      .create(createNode("anon_0"), createRelationship("s", "end", "S", "anon_0", OUTGOING))

    val expectedPlan = statefulShortestPath(topPlan, `(start)-[r:R]->(end)`)
      .allNodeScan("start")
      .build()

    plan should equal(expectedPlan)
  }

  test("Shortest match should produce an eager when there is an write/read conflict with set property") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query =
      "MATCH (a) SET a.prop = 1 WITH a MATCH ANY SHORTEST (start)-[r]->(end) WHERE start.prop = 1 RETURN end.prop2"

    val plan = planner.plan(query)
    plan should equal(
      planner.planBuilder()
        .produceResults("`end.prop2`")
        .projection("end.prop2 AS `end.prop2`")
        .statefulShortestPath(
          "start",
          "end",
          "SHORTEST 1 ((start)-[r]->(end))",
          None,
          Set.empty,
          Set.empty,
          Set("end"),
          Set("r"),
          StatefulShortestPath.Selector.Shortest(1),
          new TestNFABuilder(0, "start")
            .addTransition(0, 1, "(start)-[r]->(end)")
            .addFinalState(1)
            .build(),
          reverseGroupVariableProjections = false
        )
        .filter("start.prop = 1")
        .apply()
        .|.allNodeScan("start", "a")
        .eager(ListSet(EagernessReason.Unknown))
        .setNodeProperty("a", "prop", "1")
        .allNodeScan("a")
        .build()
    )
  }

  test("Shortest match should produce an eager when there is an write/read conflict with create relationship") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query = "CREATE (a)-[s:S]->(b) WITH b MATCH ANY SHORTEST (start)-[r]->(end) RETURN end"

    val topPlan = planner.planBuilder()
      .produceResults("end")
    val expected = statefulShortestPath(topPlan, `(start)-[r]->(end)`)
      .apply()
      .|.allNodeScan("start", "b")
      .eager(ListSet(EagernessReason.Unknown))
      .create(createNode("a"), createNode("b"), createRelationship("s", "a", "S", "b", OUTGOING))
      .argument()
      .build()

    val plan = planner.plan(query)
    plan should equal(expected)
  }

  test("Shortest match should produce an eager when there is an write/read conflict with delete") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setLabelCardinality("Label", 10)
      .setRelationshipCardinality("()-[]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query = "MATCH (a:Label) DELETE a WITH * MATCH ANY SHORTEST (start)-[r]->(end) RETURN end"

    val topPlan = planner.planBuilder()
      .produceResults("end")
    val expected = statefulShortestPath(topPlan, `(start)-[r]->(end)`)
      .apply()
      .|.allNodeScan("start", "a")
      .eager(ListSet(
        EagernessReason.ReadDeleteConflict("a"),
        EagernessReason.Unknown // end is not found as an eagerness reason in IR-eagerness since `a` is found first.
      ))
      .deleteNode("a")
      .nodeByLabelScan("a", "Label", IndexOrderNone)
      .build()

    val plan = planner.plan(query)
    plan should equal(expected)
  }

  test(
    "Shortest match should produce an eager when there is an overlap in a non nested pattern with relationship delete"
  ) {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[]->()", 10)
      .setRelationshipCardinality("()-[:R]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query =
      "MATCH ANY SHORTEST ((start)-[s]-(x) ((a)-[r:R]->(b))+(end)) DELETE s RETURN end"

    val plan = planner.plan(query)
    plan should equal(
      planner.planBuilder()
        .produceResults("end")
        .deleteRelationship("s")
        .eager(ListSet(EagernessReason.ReadDeleteConflict("s")))
        .statefulShortestPath(
          "start",
          "end",
          "SHORTEST 1 ((start)-[s]-(x) ((a)-[r:R]->(b)){1, } (end) WHERE NOT s IN `r` AND unique(`r`))",
          None,
          Set.empty,
          Set.empty,
          Set("end", "x"),
          Set("s"),
          StatefulShortestPath.Selector.Shortest(1),
          new TestNFABuilder(0, "start")
            .addTransition(0, 1, "(start)-[s]-(x)")
            .addTransition(1, 2, "(x) (a)")
            .addTransition(2, 3, "(a)-[r:R]->(b)")
            .addTransition(3, 2, "(b) (a)")
            .addTransition(3, 4, "(b) (end)")
            .addFinalState(4)
            .build(),
          false
        )
        .allNodeScan("start")
        .build()
    )
  }

  test("Shortest match should produce an eager when there is a property overlap") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[:R]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query = "MATCH ANY SHORTEST (start{prop:1})-[r:R]->(end) SET end.prop = 1 RETURN end"

    val plan = planner.plan(query)
    plan should equal(
      planner.planBuilder()
        .produceResults("end")
        .eager(ListSet(EagernessReason.Unknown))
        .setNodeProperty("end", "prop", "1")
        .eager(ListSet(EagernessReason.Unknown))
        .statefulShortestPath(
          "start",
          "end",
          "SHORTEST 1 ((start)-[r:R]->(end))",
          None,
          Set.empty,
          Set.empty,
          Set("end"),
          Set("r"),
          StatefulShortestPath.Selector.Shortest(1),
          new TestNFABuilder(0, "start")
            .addTransition(0, 1, "(start)-[r:R]->(end)")
            .addFinalState(1)
            .build(),
          reverseGroupVariableProjections = false
        )
        .filter("start.prop = 1")
        .allNodeScan("start")
        .build()
    )
  }

  test("Shortest match should produce an eager when there is a delete overlap") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[:R]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query = "MATCH ANY SHORTEST (start)-[r:R]->(end) DETACH DELETE end RETURN 1"

    val topPlan = planner.planBuilder()
      .produceResults("1")
      .projection("1 AS 1")
      .detachDeleteNode("end")
      .eager(ListSet(ReadDeleteConflict("end"))) // This eager is unnecessary since we are limited to one shortest
    val expected = statefulShortestPath(topPlan, `(start)-[r:R]->(end)`)
      .allNodeScan("start")
      .build()

    val plan = planner.plan(query)
    plan should equal(expected)
  }

  test("Shortest match should not produce an eager when there is no relationship overlap on merge") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[:T]->()", 10)
      .setRelationshipCardinality("()-[:R]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query =
      "MATCH ANY SHORTEST ((start)((a)-[r:R]->(b))+(end)) MERGE (start)-[t:T]-(end) RETURN end"

    val topPlan = planner.planBuilder()
      .produceResults("end")
      .apply()
      .|.merge(Seq(), Seq(createRelationship("t", "start", "T", "end", BOTH)), Seq(), Seq(), Set("start", "end"))
      .|.expandInto("(start)-[t:T]-(end)")
      .|.argument("start", "end")
    val expected = statefulShortestPath(topPlan, `((start)((a)-[r:R]->(b))+(end))`)
      .allNodeScan("start")
      .build()

    val plan = planner.plan(query)
    plan should equal(expected)
  }

  test("Shortest match should produce an eager when there is a relationship overlap on merge") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setRelationshipCardinality("()-[:T]->()", 10)
      .setRelationshipCardinality("()-[:R]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query =
      "MATCH ANY SHORTEST ((start)((a)-[r:R]->(b))+(end)) MERGE (start)-[t:R]-(end) RETURN end"

    val topPlan = planner.planBuilder()
      .produceResults("end")
      .apply()
      .|.merge(Seq(), Seq(createRelationship("t", "start", "R", "end", BOTH)), Seq(), Seq(), Set("start", "end"))
      .|.expandInto("(start)-[t:R]-(end)")
      .|.argument("start", "end")
      .eager(ListSet(EagernessReason.Unknown))
    val expected = statefulShortestPath(topPlan, `((start)((a)-[r:R]->(b))+(end))`)
      .allNodeScan("start")
      .build()

    val plan = planner.plan(query)
    plan should equal(expected)
  }

  test("Shortest match produces an unnecessary eager when there is no overlap on the inner qpp relationship") {
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setLabelCardinality("Label", 10)
      .setRelationshipCardinality("()-[:R]->()", 10)
      .setRelationshipCardinality("()-[]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query =
      "MATCH ANY SHORTEST ((start:!Label)((a:!Label)-[r:!R]->(b:!Label))+(end:!Label)) MERGE (start)-[t:R]-(end) RETURN end"

    val plan = planner.plan(query)
    plan should equal(
      planner.planBuilder()
        .produceResults("end")
        .apply()
        .|.merge(Seq(), Seq(createRelationship("t", "start", "R", "end", BOTH)), Seq(), Seq(), Set("start", "end"))
        .|.expandInto("(start)-[t:R]-(end)")
        .|.argument("start", "end")
        .eager(ListSet(EagernessReason.Unknown)) // This unnecessary eager is only in IR Eager because we do not handle type expressions in IR Eagerness analysis
        .statefulShortestPath(
          "start",
          "end",
          "SHORTEST 1 ((start) ((a)-[r]->(b) WHERE NOT `a`:Label AND NOT `r`:R AND NOT `b`:Label){1, } (end) WHERE NOT end:Label AND unique(`r`))",
          None,
          Set.empty,
          Set.empty,
          Set("end"),
          Set.empty,
          StatefulShortestPath.Selector.Shortest(1),
          new TestNFABuilder(0, "start")
            .addTransition(0, 1, "(start) (a WHERE NOT a:Label)")
            .addTransition(1, 2, "(a)-[r WHERE NOT r:R]->(b WHERE NOT b:Label)")
            .addTransition(2, 1, "(b) (a WHERE NOT a:Label)")
            .addTransition(2, 3, "(b) (end WHERE NOT end:Label)")
            .addFinalState(3)
            .build(),
          false
        )
        .filter("NOT start:Label")
        .allNodeScan("start")
        .build()
    )
  }

  test("Shortest match produces an unnecessary eager on write/read for delete when there is no overlap") {
    // We cannot find the leafPlans for variables within a SPP so we plan an eager for each found variable.
    // This is only applicable when we don't have the deleted node as an argument, then we would instead just mention the overlap on the deleted node.
    val planner = plannerBuilder()
      .setAllNodesCardinality(10)
      .setLabelCardinality("Label", 10)
      .setRelationshipCardinality("()-[:R]->()", 10)
      .setRelationshipCardinality("()-[]->()", 10)
      .withSetting(GraphDatabaseInternalSettings.cypher_eager_analysis_implementation, EagerAnalysisImplementation.IR)
      .addSemanticFeature(SemanticFeature.GpmShortestPath)
      .build()

    val query =
      "MATCH (x:Label) DELETE x WITH 1 as z MATCH ANY SHORTEST ((start:!Label)((a:!Label)-[r:R]->(b:!Label))+(end:!Label)) return end"

    val plan = planner.plan(query)
    plan should equal(
      planner.planBuilder()
        .produceResults("end")
        .statefulShortestPath(
          "start",
          "end",
          "SHORTEST 1 ((start) ((a)-[r:R]->(b) WHERE NOT `a`:Label AND NOT `b`:Label){1, } (end) WHERE NOT end:Label AND unique(`r`))",
          None,
          Set.empty,
          Set.empty,
          Set("end"),
          Set.empty,
          StatefulShortestPath.Selector.Shortest(1),
          new TestNFABuilder(0, "start")
            .addTransition(0, 1, "(start) (a WHERE NOT a:Label)")
            .addTransition(1, 2, "(a)-[r:R]->(b WHERE NOT b:Label)")
            .addTransition(2, 1, "(b) (a WHERE NOT a:Label)")
            .addTransition(2, 3, "(b) (end WHERE NOT end:Label)")
            .addFinalState(3)
            .build(),
          false
        )
        .filter("NOT start:Label")
        .apply()
        .|.allNodeScan("start", "z")
        .projection("1 AS z")
        .eager(ListSet(
          EagernessReason.ReadDeleteConflict("start"),
          EagernessReason.ReadDeleteConflict("end"),
          EagernessReason.ReadDeleteConflict("a"),
          EagernessReason.ReadDeleteConflict("b"),
          EagernessReason.Unknown
        ))
        .deleteNode("x")
        .nodeByLabelScan("x", "Label", IndexOrderNone)
        .build()
    )
  }
}
