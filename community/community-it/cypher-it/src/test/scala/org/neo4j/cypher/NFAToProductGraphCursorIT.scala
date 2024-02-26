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
package org.neo4j.cypher

import org.eclipse.collections.impl.block.factory.primitive.IntPredicates
import org.neo4j.configuration.GraphDatabaseInternalSettings
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.False
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.NotEquals
import org.neo4j.cypher.internal.expressions.Ors
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.expressions.True
import org.neo4j.cypher.internal.expressions.UnPositionedVariable.varFor
import org.neo4j.cypher.internal.expressions.UnsignedDecimalIntegerLiteral
import org.neo4j.cypher.internal.logical.plans.Expand.VariablePredicate
import org.neo4j.cypher.internal.logical.plans.NFA
import org.neo4j.cypher.internal.logical.plans.NFABuilder
import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration
import org.neo4j.cypher.internal.planner.spi.ReadTokenContext
import org.neo4j.cypher.internal.runtime.CypherRuntimeConfiguration
import org.neo4j.cypher.internal.runtime.ast.ConstantExpressionVariable
import org.neo4j.cypher.internal.runtime.ast.TemporaryExpressionVariable
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.runtime.interpreted.commands.CommandNFA
import org.neo4j.cypher.internal.runtime.interpreted.commands.convert.CommunityExpressionConverter
import org.neo4j.cypher.internal.runtime.interpreted.commands.convert.ExpressionConverters
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.runtime.slotted.SlottedRow
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.internal.util.symbols
import org.neo4j.graphdb.Entity
import org.neo4j.graphdb.config.Setting
import org.neo4j.internal.kernel.api.NodeCursor
import org.neo4j.internal.kernel.api.Read
import org.neo4j.internal.kernel.api.RelationshipTraversalCursor
import org.neo4j.internal.kernel.api.exceptions.LabelNotFoundKernelException
import org.neo4j.internal.kernel.api.helpers.ProductGraphTraversalCursorTest
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.NodeJuxtaposition
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.ProductGraphTraversalCursor
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.RelationshipExpansion
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.State
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.State.VarName
import org.neo4j.kernel.api.exceptions.PropertyKeyNotFoundException
import org.neo4j.kernel.impl.coreapi.InternalTransaction
import org.neo4j.memory.EmptyMemoryTracker
import org.neo4j.token.api.TokenConstants
import org.neo4j.values.AnyValue

class NFAToProductGraphCursorIT extends ExecutionEngineFunSuite {

  override def databaseConfig(): Map[Setting[_], Object] = super.databaseConfig() ++ Map(
    GraphDatabaseInternalSettings.trace_cursors -> java.lang.Boolean.TRUE
  )

  private val converters =
    new ExpressionConverters(CommunityExpressionConverter(
      ReadTokenContext.EMPTY,
      new AnonymousVariableNameGenerator(),
      CypherRuntimeConfiguration.defaultConfiguration
    ))

  private val expressionVariables = Array.fill[AnyValue](10)(null)

  private val truePred = Some(VariablePredicate(ConstantExpressionVariable(0, "not used"), True()(InputPosition.NONE)))

  private val falsePred =
    Some(VariablePredicate(ConstantExpressionVariable(0, "not used"), False()(InputPosition.NONE)))

  test("should traverse two hops") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      // (start)-[r1:R1]->(a1)-[r2:R2]->(f1)
      val start = createNode()
      val a1 = createNode()
      val a2 = createNode()
      val r1 = relate(start, a1, "R1")
      val r2 = relate(a1, a2, "R2")

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)
      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("s0"))
      val s1 = nfaBuilder.addAndGetState(varFor("s1"))
      val s2 = nfaBuilder.addAndGetState(varFor("s2"))
      val s0 = nfaBuilder.getStartState

      nfaBuilder.addTransition(
        s0,
        s1,
        NFA.RelationshipExpansionPredicate(
          varFor("r1"),
          None,
          Seq(relTypeName("R1")),
          SemanticDirection.OUTGOING,
          None
        )
      )

      nfaBuilder.addTransition(
        s1,
        s2,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          None,
          Seq(relTypeName("R2")),
          SemanticDirection.OUTGOING,
          None
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      val expected =
        new ProductGraphTraversalCursorTest.ProductGraphBuilder()
          .addNode(start.getId, conv(s0))
          .addNode(a1.getId, conv(s1))
          .addNode(a2.getId, conv(s2))
          .addRelationship(start.getId, conv(s0), r1.getId, a1.getId, conv(s1))
          .addRelationship(a1.getId, conv(s1), r2.getId, a2.getId, conv(s2))
          .build

      actual.assertSame(expected)
    }
  }

  test("should filter on type") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      // (start)-[r1:R1]->(a1)-[r2:R2]->(f1)
      val start = createNode()
      val a1 = createNode()
      val a2 = createNode()
      val r1 = relate(start, a1, "R1")
      val _ = relate(a1, a2, "R2")

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)

      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("s0"))
      val s1 = nfaBuilder.addAndGetState(varFor("s1"))
      val s2 = nfaBuilder.addAndGetState(varFor("s2"))
      val s0 = nfaBuilder.getStartState

      nfaBuilder.addTransition(
        s0,
        s1,
        NFA.RelationshipExpansionPredicate(
          varFor("r1"),
          None,
          Seq(relTypeName("R1")),
          SemanticDirection.OUTGOING,
          None
        )
      )

      nfaBuilder.addTransition(
        s1,
        s2,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          None,
          Seq(relTypeName("R1")),
          SemanticDirection.OUTGOING,
          None
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      val expected =
        new ProductGraphTraversalCursorTest.ProductGraphBuilder()
          .addNode(start.getId, conv(s0))
          .addNode(a1.getId, conv(s1))
          .addRelationship(start.getId, conv(s0), r1.getId, a1.getId, conv(s1))
          .build

      actual.assertSame(expected)
    }
  }

  test("should filter on direction") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      // (start)-[r1:R1]->(a1)-[r2:R2]->(f1)
      val start = createNode()
      val a1 = createNode()
      val a2 = createNode()
      val r1 = relate(start, a1, "R1")
      val _ = relate(a1, a2, "R2")

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)

      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("s0"))
      val s1 = nfaBuilder.addAndGetState(varFor("s1"))
      val s2 = nfaBuilder.addAndGetState(varFor("s2"))
      val s0 = nfaBuilder.getStartState

      nfaBuilder.addTransition(
        s0,
        s1,
        NFA.RelationshipExpansionPredicate(
          varFor("r1"),
          None,
          Seq(relTypeName("R1")),
          SemanticDirection.OUTGOING,
          None
        )
      )

      nfaBuilder.addTransition(
        s1,
        s2,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          None,
          Seq(relTypeName("R2")),
          SemanticDirection.INCOMING,
          None
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      val expected =
        new ProductGraphTraversalCursorTest.ProductGraphBuilder()
          .addNode(start.getId, conv(s0))
          .addNode(a1.getId, conv(s1))
          .addRelationship(start.getId, conv(s0), r1.getId, a1.getId, conv(s1))
          .build

      actual.assertSame(expected)
    }
  }

  test("should filter on rel predicate") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      // (start)-[r1:R1]->(a1)-[r2:R2]->(f1)
      val start = createNode()
      val a1 = createNode()
      val a2 = createNode()
      val r1 = relate(start, a1, "R1")
      val _ = relate(a1, a2, "R2")

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)

      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("s0"))
      val s1 = nfaBuilder.addAndGetState(varFor("s1"))
      val s2 = nfaBuilder.addAndGetState(varFor("s2"))
      val s0 = nfaBuilder.getStartState

      nfaBuilder.addTransition(
        s0,
        s1,
        NFA.RelationshipExpansionPredicate(
          varFor("r1"),
          None,
          Seq(relTypeName("R1")),
          SemanticDirection.OUTGOING,
          None
        )
      )

      nfaBuilder.addTransition(
        s1,
        s2,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          falsePred,
          Seq(relTypeName("R2")),
          SemanticDirection.OUTGOING,
          None
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      val expected =
        new ProductGraphTraversalCursorTest.ProductGraphBuilder()
          .addNode(start.getId, conv(s0))
          .addNode(a1.getId, conv(s1))
          .addRelationship(start.getId, conv(s0), r1.getId, a1.getId, conv(s1))
          .build

      actual.assertSame(expected)
    }
  }

  test("should filter on node predicate") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      // (start)-[r1:R1]->(a1)-[r2:R2]->(f1)
      val start = createNode()
      val a1 = createNode()
      val a2 = createNode()
      val r1 = relate(start, a1, "R1")
      val _ = relate(a1, a2, "R2")

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)

      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("s0"))
      val s1 = nfaBuilder.addAndGetState(varFor("s1"))
      val s2 = nfaBuilder.addAndGetState(varFor("s2"))
      val s0 = nfaBuilder.getStartState

      nfaBuilder.addTransition(
        s0,
        s1,
        NFA.RelationshipExpansionPredicate(
          varFor("r1"),
          None,
          Seq(relTypeName("R1")),
          SemanticDirection.OUTGOING,
          None
        )
      )

      nfaBuilder.addTransition(
        s1,
        s2,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          truePred,
          Seq(relTypeName("R2")),
          SemanticDirection.OUTGOING,
          falsePred
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      val expected =
        new ProductGraphTraversalCursorTest.ProductGraphBuilder()
          .addNode(start.getId, conv(s0))
          .addNode(a1.getId, conv(s1))
          .addRelationship(start.getId, conv(s0), r1.getId, a1.getId, conv(s1))
          .build

      actual.assertSame(expected)
    }
  }

  test("should handle multiple types in multiple directions") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      //                              -[o1:O1]->
      //       _________<________     -[o2:O3]->
      //     /                   \    -[o3:O2]->
      // [l{1,2,3}:L{1,2,3}]   (start)            (a1)
      //     \_______>___________/    <-[i1:I1]-
      //                              <-[i2:I3]-
      //                              <-[i3:I2]-

      val start = createNode()
      val a1 = createNode()

      // Outgoing
      val o1 = relate(start, a1, "O1")
      val _ = relate(start, a1, "O2")
      val o3 = relate(start, a1, "O3")

      // Incoming
      val i1 = relate(a1, start, "I1")
      val i2 = relate(a1, start, "I2")
      val i3 = relate(a1, start, "I3")

      // Loop
      val l1 = relate(start, start, "L1")
      val l2 = relate(start, start, "L2")
      val l3 = relate(start, start, "L3")

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)

      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("s0"))
      val s1 = nfaBuilder.addAndGetState(varFor("s1"))
      val s2 = nfaBuilder.addAndGetState(varFor("s2"))
      val s3 = nfaBuilder.addAndGetState(varFor("s3"))
      val s4 = nfaBuilder.addAndGetState(varFor("s4"))
      val s0 = nfaBuilder.getStartState
      nfaBuilder.addFinalState(s4)

      nfaBuilder.addTransition(
        s0,
        s1,
        NFA.RelationshipExpansionPredicate(
          varFor("r1"),
          truePred,
          Seq(relTypeName("O1")),
          SemanticDirection.OUTGOING,
          truePred
        )
      )

      nfaBuilder.addTransition(
        s0,
        s2,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          truePred,
          Seq(relTypeName("I1"), relTypeName("I2")),
          SemanticDirection.INCOMING,
          truePred
        )
      )

      nfaBuilder.addTransition(
        s0,
        s3,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          truePred,
          Seq(relTypeName("L1"), relTypeName("I3")),
          SemanticDirection.BOTH,
          truePred
        )
      )

      nfaBuilder.addTransition(
        s3,
        s4,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          truePred,
          Seq(relTypeName("O3"), relTypeName("L2")),
          SemanticDirection.INCOMING,
          truePred
        )
      )

      nfaBuilder.addTransition(
        s4,
        s0,
        NFA.RelationshipExpansionPredicate(
          varFor("r2"),
          truePred,
          Seq(relTypeName("L3")),
          SemanticDirection.INCOMING,
          truePred
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      // then
      val expected = new ProductGraphTraversalCursorTest.ProductGraphBuilder()
        .addNode(start.getId, conv(s0))
        .addNode(a1.getId, conv(s1))
        .addNode(a1.getId, conv(s2))
        .addNode(a1.getId, conv(s3))
        .addNode(start.getId, conv(s3))
        .addNode(start.getId, conv(s4))
        .addRelationship(start.getId, conv(s0), o1.getId, a1.getId, conv(s1))
        .addRelationship(start.getId, conv(s0), i1.getId, a1.getId, conv(s2))
        .addRelationship(start.getId, conv(s0), i2.getId, a1.getId, conv(s2))
        .addRelationship(start.getId, conv(s0), i3.getId, a1.getId, conv(s3))
        .addRelationship(start.getId, conv(s0), l1.getId, start.getId, conv(s3))
        .addRelationship(a1.getId, conv(s3), o3.getId, start.getId, conv(s4))
        .addRelationship(start.getId, conv(s3), l2.getId, start.getId, conv(s4))
        .addRelationship(start.getId, conv(s4), l3.getId, start.getId, conv(s0))
        .build()

      actual.assertSame(expected)
    }
  }

  test("node juxtaposition should filter on node predicate") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      val start = createNode()

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)

      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("s0"))
      val s1 = nfaBuilder.addAndGetState(varFor("s1"))
      val s2 = nfaBuilder.addAndGetState(varFor("s2"))
      val s0 = nfaBuilder.getStartState

      nfaBuilder.addTransition(
        s0,
        s1,
        NFA.NodeJuxtapositionPredicate(
          truePred
        )
      )

      nfaBuilder.addTransition(
        s0,
        s2,
        NFA.NodeJuxtapositionPredicate(
          falsePred
        )
      )

      nfaBuilder.addTransition(
        s1,
        s2,
        NFA.NodeJuxtapositionPredicate(
          falsePred
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      val expected =
        new ProductGraphTraversalCursorTest.ProductGraphBuilder()
          .addNode(start.getId, conv(s0))
          .addNode(start.getId, conv(s1))
          .addJuxtaposition(start.getId, conv(s0), conv(s1))
          .build

      actual.assertSame(expected)
    }
  }

  test("complicated graph and automata") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      //          _____________________________________
      //         |                                     |
      //      (start)<-[r2s:R2s]-(n2)                  |
      //         |              /                      |
      //     [rs1:Rs1]    [r21:R21]                    |
      //         |    _____/          _____            |
      //         V  V               /       \          |
      //       (n1)-[r13:R13]->(n3)<-[r33:R33]     [rs5:Rs5]
      //         |               |                     |
      //     [r14:R14]       [r35:R35]                 |
      //         |               |                     |
      //         V       .       V                     |
      //       (n4)-[r45:R45]->(n5)<-------------------|

      val start = createNode()
      val n1 = createNode()
      val n2 = createNode()
      val n3 = createNode()
      val n4 = createNode()
      val n5 = createNode()

      val rs1 = relate(start, n1, "Rs1")
      val r2s = relate(n2, start, "R2s")
      val rs5 = relate(start, n5, "Rs5")
      val r21 = relate(n2, n1, "R21")
      val r13 = relate(n1, n3, "R13")
      val r14 = relate(n1, n4, "R14")
      val r33 = relate(n3, n3, "R33")
      val r35 = relate(n3, n5, "R35")
      val r45 = relate(n4, n5, "R45")

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)

      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("nj0"))
      val nj0 = nfaBuilder.getStartState

      val nj1 = nfaBuilder.addAndGetState(varFor("nj1"))
      val nj2 = nfaBuilder.addAndGetState(varFor("nj2"))
      val nj3 = nfaBuilder.addAndGetState(varFor("nj3"))
      val nj4 = nfaBuilder.addAndGetState(varFor("nj4"))
      val nj5 = nfaBuilder.addAndGetState(varFor("nj5"))

      val re0 = nfaBuilder.addAndGetState(varFor("re0"))
      val re1 = nfaBuilder.addAndGetState(varFor("re1"))
      val re2 = nfaBuilder.addAndGetState(varFor("re2"))
      val _ = nfaBuilder.addAndGetState(varFor("re3"))
      val _ = nfaBuilder.addAndGetState(varFor("re4"))

      nfaBuilder.addTransition(
        nj0,
        nj1,
        NFA.NodeJuxtapositionPredicate(
          Some(varPred(0, eq(idOfExprVar(0), idStatically(n1))))
        )
      )
      nfaBuilder.addTransition(
        nj0,
        nj2,
        NFA.NodeJuxtapositionPredicate(
          Some(varPred(0, eq(idOfExprVar(0), idStatically(start))))
        )
      )
      nfaBuilder.addTransition(
        nj1,
        nj2,
        NFA.NodeJuxtapositionPredicate(
          truePred
        )
      )
      nfaBuilder.addTransition(
        nj2,
        re0,
        NFA.NodeJuxtapositionPredicate(
          truePred
        )
      )
      nfaBuilder.addTransition(
        re0,
        nj4,
        NFA.RelationshipExpansionPredicate(
          varFor("re0->nj4"),
          Some(varPred(0, eq(function("startNode", exprVar(0)), function("endNode", exprVar(0))))),
          Seq(), // All types
          SemanticDirection.BOTH,
          truePred
        )
      )
      nfaBuilder.addTransition(
        re0,
        nj3,
        NFA.RelationshipExpansionPredicate(
          varFor("re0->nj3"),
          Some(varPred(
            0,
            ors(
              eq(idOfExprVar(0), idStatically(rs5)),
              eq(idOfExprVar(0), idStatically(r2s)),
              eq(idOfExprVar(0), idStatically(rs1))
            )
          )),
          Seq(), // All types
          SemanticDirection.BOTH,
          Some(varPred(0, neq(idOfExprVar(0), idStatically(n5))))
        )
      )
      nfaBuilder.addTransition(
        nj3,
        re1,
        NFA.NodeJuxtapositionPredicate(
          Some(varPred(0, neq(idOfExprVar(0), idStatically(start))))
        )
      )
      nfaBuilder.addTransition(
        re1,
        re2,
        NFA.RelationshipExpansionPredicate(
          varFor("re1->re2"),
          truePred,
          Seq(), // All types
          SemanticDirection.BOTH,
          None // Another (preferred) way to specify always true
        )
      )
      nfaBuilder.addTransition(
        re2,
        re2,
        NFA.RelationshipExpansionPredicate(
          varFor("re2->re2"),
          truePred,
          Seq(), // All types
          SemanticDirection.OUTGOING,
          None // Another (preferred) way to specify always true
        )
      )
      nfaBuilder.addTransition(
        re2,
        nj5,
        NFA.RelationshipExpansionPredicate(
          varFor("re2->nj5"),
          Some(varPred(0, eq(function("startNode", exprVar(0)), function("endNode", exprVar(0))))),
          Seq(), // All types
          SemanticDirection.OUTGOING,
          None // Another (preferred) way to specify always true
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      val expected =
        new ProductGraphTraversalCursorTest.ProductGraphBuilder()
          .addNode(start.getId, conv(nj0))
          .addJuxtaposition(start.getId, conv(nj0), conv(nj2))
          .addNode(start.getId, conv(nj2))
          .addJuxtaposition(start.getId, conv(nj2), conv(re0))
          .addNode(start.getId, conv(re0))
          .addRelationship(start.getId, conv(re0), r2s.getId, n2.getId, conv(nj3))
          .addRelationship(start.getId, conv(re0), rs1.getId, n1.getId, conv(nj3))
          .addNode(n2.getId, conv(nj3))
          .addNode(n1.getId, conv(nj3))
          .addJuxtaposition(n2.getId, conv(nj3), conv(re1))
          .addJuxtaposition(n1.getId, conv(nj3), conv(re1))
          .addNode(n2.getId, conv(re1))
          .addNode(n1.getId, conv(re1))
          .addRelationship(n1.getId, conv(re1), rs1.getId, start.getId, conv(re2))
          .addRelationship(n1.getId, conv(re1), r21.getId, n2.getId, conv(re2))
          .addRelationship(n1.getId, conv(re1), r13.getId, n3.getId, conv(re2))
          .addRelationship(n1.getId, conv(re1), r14.getId, n4.getId, conv(re2))
          .addRelationship(n2.getId, conv(re1), r21.getId, n1.getId, conv(re2))
          .addRelationship(n2.getId, conv(re1), r2s.getId, start.getId, conv(re2))
          .addNode(start.getId, conv(re2))
          .addNode(n2.getId, conv(re2))
          .addNode(n3.getId, conv(re2))
          .addNode(n4.getId, conv(re2))
          .addNode(n1.getId, conv(re2))
          .addRelationship(start.getId, conv(re2), rs1.getId, n1.getId, conv(re2))
          .addRelationship(start.getId, conv(re2), rs5.getId, n5.getId, conv(re2))
          .addRelationship(n1.getId, conv(re2), r13.getId, n3.getId, conv(re2))
          .addRelationship(n1.getId, conv(re2), r14.getId, n4.getId, conv(re2))
          .addRelationship(n2.getId, conv(re2), r2s.getId, start.getId, conv(re2))
          .addRelationship(n2.getId, conv(re2), r21.getId, n1.getId, conv(re2))
          .addRelationship(n3.getId, conv(re2), r33.getId, n3.getId, conv(re2))
          .addRelationship(n3.getId, conv(re2), r35.getId, n5.getId, conv(re2))
          .addRelationship(n4.getId, conv(re2), r45.getId, n5.getId, conv(re2))
          .addNode(n5.getId, conv(re2))
          .addRelationship(n3.getId, conv(re2), r33.getId, n3.getId, conv(nj5))
          .addNode(n3.getId, conv(nj5))
          .build()

      actual.assertSame(expected)
    }
  }

  test("complicated graph and automata mixed transition") {

    withEverything { (readTokenContext, read, queryState, nodeCursor, _, pgCursor) =>
      //          _____________________________________
      //         |                                     |
      //      (start)<-[r2s:R2s]-(n2)                  |
      //         |              /                      |
      //     [rs1:Rs1]    [r21:R21]                    |
      //         |    _____/          _____            |
      //         V  V               /       \          |
      //       (n1)-[r13:R13]->(n3)<-[r33:R33]     [rs5:Rs5]
      //         |               |                     |
      //     [r14:R14]       [r35:R35]                 |
      //         |               |                     |
      //         V       .       V                     |
      //       (n4)-[r45:R45]->(n5)<-------------------|

      val start = createNode()
      val n1 = createNode()
      val n2 = createNode()
      val n3 = createNode()
      val n4 = createNode()
      val n5 = createNode()

      val rs1 = relate(start, n1, "Rs1")
      val r2s = relate(n2, start, "R2s")
      val rs5 = relate(start, n5, "Rs5")
      val r21 = relate(n2, n1, "R21")
      val r13 = relate(n1, n3, "R13")
      val r14 = relate(n1, n4, "R14")
      val r33 = relate(n3, n3, "R33")
      val r35 = relate(n3, n5, "R35")
      val r45 = relate(n4, n5, "R45")

      val slots = SlotConfiguration.empty
        .newLong("start", nullable = true, symbols.CTNode)

      val context = SlottedRow(slots)
      context.setLongAt(slots.getLongOffsetFor("start"), start.getId)

      val nfaBuilder = new NFABuilder(varFor("s0"))
      val s0 = nfaBuilder.getStartState

      val s1 = nfaBuilder.addAndGetState(varFor("s1"))
      val s2 = nfaBuilder.addAndGetState(varFor("s2"))
      val s3 = nfaBuilder.addAndGetState(varFor("s3"))
      val s4 = nfaBuilder.addAndGetState(varFor("s4"))
      val s5 = nfaBuilder.addAndGetState(varFor("s5"))

      val s6 = nfaBuilder.addAndGetState(varFor("s6"))
      val s7 = nfaBuilder.addAndGetState(varFor("s7"))
      val s8 = nfaBuilder.addAndGetState(varFor("s8"))
      val _ = nfaBuilder.addAndGetState(varFor("s9"))
      val _ = nfaBuilder.addAndGetState(varFor("s10"))

      nfaBuilder.addTransition(
        s0,
        s1,
        NFA.NodeJuxtapositionPredicate(
          Some(varPred(0, eq(idOfExprVar(0), idStatically(n1))))
        )
      )
      nfaBuilder.addTransition(
        s0,
        s2,
        NFA.NodeJuxtapositionPredicate(
          Some(varPred(0, eq(idOfExprVar(0), idStatically(start))))
        )
      )
      nfaBuilder.addTransition(
        s0,
        s4,
        NFA.RelationshipExpansionPredicate(
          varFor("s0->s4"),
          truePred,
          Seq(),
          SemanticDirection.INCOMING,
          truePred
        )
      )

      nfaBuilder.addTransition(
        s1,
        s2,
        NFA.NodeJuxtapositionPredicate(
          truePred
        )
      )
      nfaBuilder.addTransition(
        s2,
        s6,
        NFA.NodeJuxtapositionPredicate(
          truePred
        )
      )

      // s4 has mixed transitions
      nfaBuilder.addTransition(
        s4,
        s5,
        NFA.NodeJuxtapositionPredicate(
          Some(varPred(0, eq(idOfExprVar(0), idStatically(n2))))
        )
      )
      nfaBuilder.addTransition(
        s4,
        s3,
        NFA.RelationshipExpansionPredicate(
          varFor("s4->s3"),
          truePred,
          Seq(),
          SemanticDirection.OUTGOING,
          truePred
        )
      )

      nfaBuilder.addTransition(
        s6,
        s4,
        NFA.RelationshipExpansionPredicate(
          varFor("s6->s4"),
          Some(varPred(0, eq(function("startNode", exprVar(0)), function("endNode", exprVar(0))))),
          Seq(), // All types
          SemanticDirection.BOTH,
          truePred
        )
      )
      nfaBuilder.addTransition(
        s6,
        s3,
        NFA.RelationshipExpansionPredicate(
          varFor("s6->s3"),
          Some(varPred(
            0,
            ors(
              eq(idOfExprVar(0), idStatically(rs5)),
              eq(idOfExprVar(0), idStatically(r2s)),
              eq(idOfExprVar(0), idStatically(rs1))
            )
          )),
          Seq(), // All types
          SemanticDirection.BOTH,
          Some(varPred(0, neq(idOfExprVar(0), idStatically(n5))))
        )
      )
      nfaBuilder.addTransition(
        s3,
        s7,
        NFA.NodeJuxtapositionPredicate(
          Some(varPred(0, neq(idOfExprVar(0), idStatically(start))))
        )
      )
      nfaBuilder.addTransition(
        s7,
        s8,
        NFA.RelationshipExpansionPredicate(
          varFor("s8->s7"),
          truePred,
          Seq(), // All types
          SemanticDirection.BOTH,
          None // Another (preferred) way to specify always true
        )
      )
      nfaBuilder.addTransition(
        s8,
        s8,
        NFA.RelationshipExpansionPredicate(
          varFor("s8->s8"),
          truePred,
          Seq(), // All types
          SemanticDirection.OUTGOING,
          None // Another (preferred) way to specify always true
        )
      )
      nfaBuilder.addTransition(
        s8,
        s5,
        NFA.RelationshipExpansionPredicate(
          varFor("s8->s5"),
          Some(varPred(0, eq(function("startNode", exprVar(0)), function("endNode", exprVar(0))))),
          Seq(), // All types
          SemanticDirection.OUTGOING,
          None // Another (preferred) way to specify always true
        )
      )

      val nfa = nfaBuilder.build()

      val compiledS0 =
        CommandNFA.fromLogicalNFA(Id.INVALID_ID)(
          nfa,
          IntPredicates.alwaysFalse(),
          converters,
          readTokenContext
        ).compile(context, queryState)

      val actual = ProductGraphTraversalCursorTest.ProductGraph.fromCursors(
        start.getId,
        compiledS0,
        pgCursor,
        nodeCursor,
        read
      )

      val expected =
        new ProductGraphTraversalCursorTest.ProductGraphBuilder()
          .addNode(start.getId, conv(s0))
          .addJuxtaposition(start.getId, conv(s0), conv(s2))
          .addNode(start.getId, conv(s2))
          .addJuxtaposition(start.getId, conv(s2), conv(s6))
          .addNode(start.getId, conv(s6))
          .addRelationship(start.getId, conv(s0), r2s.getId, n2.getId, conv(s4))
          .addNode(n2.getId, conv(s4))
          .addJuxtaposition(n2.getId, conv(s4), conv(s5))
          .addNode(n2.getId, conv(s5))
          .addRelationship(start.getId, conv(s6), r2s.getId, n2.getId, conv(s3))
          .addNode(n2.getId, conv(s3))
          .addRelationship(n2.getId, conv(s4), r2s.getId, start.getId, conv(s3))
          .addNode(start.getId, conv(s3))
          .addRelationship(n2.getId, conv(s4), r21.getId, n1.getId, conv(s3))
          .addRelationship(start.getId, conv(s6), rs1.getId, n1.getId, conv(s3))
          .addNode(n1.getId, conv(s3))
          .addJuxtaposition(n2.getId, conv(s3), conv(s7))
          .addNode(n2.getId, conv(s7))
          .addJuxtaposition(n1.getId, conv(s3), conv(s7))
          .addNode(n1.getId, conv(s7))
          .addRelationship(n1.getId, conv(s7), rs1.getId, start.getId, conv(s8))
          .addRelationship(n2.getId, conv(s7), r2s.getId, start.getId, conv(s8))
          .addNode(start.getId, conv(s8))
          .addRelationship(n1.getId, conv(s7), r21.getId, n2.getId, conv(s8))
          .addNode(n2.getId, conv(s8))
          .addRelationship(n1.getId, conv(s7), r13.getId, n3.getId, conv(s8))
          .addNode(n3.getId, conv(s8))
          .addRelationship(n1.getId, conv(s7), r14.getId, n4.getId, conv(s8))
          .addNode(n4.getId, conv(s8))
          .addRelationship(n2.getId, conv(s7), r21.getId, n1.getId, conv(s8))
          .addNode(n1.getId, conv(s8))
          .addRelationship(start.getId, conv(s8), rs1.getId, n1.getId, conv(s8))
          .addRelationship(start.getId, conv(s8), rs5.getId, n5.getId, conv(s8))
          .addRelationship(n1.getId, conv(s8), r13.getId, n3.getId, conv(s8))
          .addRelationship(n1.getId, conv(s8), r14.getId, n4.getId, conv(s8))
          .addRelationship(n2.getId, conv(s8), r2s.getId, start.getId, conv(s8))
          .addRelationship(n2.getId, conv(s8), r21.getId, n1.getId, conv(s8))
          .addRelationship(n3.getId, conv(s8), r33.getId, n3.getId, conv(s8))
          .addRelationship(n3.getId, conv(s8), r35.getId, n5.getId, conv(s8))
          .addRelationship(n4.getId, conv(s8), r45.getId, n5.getId, conv(s8))
          .addNode(n5.getId, conv(s8))
          .addRelationship(n3.getId, conv(s8), r33.getId, n3.getId, conv(s5))
          .addNode(n3.getId, conv(s5))
          .build()

      expected.assertSame(actual)
    }
  }

  private val pos = InputPosition.NONE

  def eq(l: Expression, r: Expression): Expression = {
    Equals(l, r)(pos)
  }

  def neq(l: Expression, r: Expression): Expression = {
    NotEquals(l, r)(pos)
  }

  def ors(es: Expression*): Expression = {
    Ors(es)(pos)
  }

  def ands(es: Expression*): Expression = {
    Ors(es)(pos)
  }

  def idStatically(entity: Entity): Expression = {
    UnsignedDecimalIntegerLiteral(entity.getId.toString)(pos)
  }

  def exprVar(offset: Int): TemporaryExpressionVariable = {
    TemporaryExpressionVariable(offset, s"offset-$offset")
  }

  def varPred(offset: Int, p: Expression): VariablePredicate = {
    VariablePredicate(exprVar(offset), p)
  }

  def idOfExprVar(offset: Int): Expression = {
    function("id", exprVar(offset))
  }

  def function(name: String, args: Expression*): FunctionInvocation =
    FunctionInvocation(FunctionName(name)(pos), distinct = false, args.toIndexedSeq)(pos)

  def conv(state: NFABuilder.State): productgraph.State = {
    new State(
      state.id,
      new VarName(state.variable.name, false),
      new Array[NodeJuxtaposition](0),
      new Array[RelationshipExpansion](0),
      false,
      false
    )

  }

  def withEverything[T](f: (
    ReadTokenContext,
    Read,
    QueryState,
    NodeCursor,
    RelationshipTraversalCursor,
    ProductGraphTraversalCursor
  ) => T): T = {
    withTx { tx =>
      var nodeCursor: NodeCursor = null
      var relCursor: RelationshipTraversalCursor = null
      var pgCursor: ProductGraphTraversalCursor = null
      try {
        val cursorFactory = tx.kernelTransaction().cursors()
        val cursorContext = tx.kernelTransaction().cursorContext()
        nodeCursor = cursorFactory.allocateNodeCursor(cursorContext)
        relCursor = cursorFactory.allocateRelationshipTraversalCursor(cursorContext)
        pgCursor = new ProductGraphTraversalCursor(relCursor, EmptyMemoryTracker.INSTANCE)

        QueryStateHelper.withQueryState(
          graph,
          tx,
          f = qs => {
            f(
              txToReadTokenContext(tx),
              tx.kernelTransaction().dataRead(),
              qs,
              nodeCursor,
              relCursor,
              pgCursor
            )
          },
          expressionVariables = expressionVariables
        )
      } finally {
        nodeCursor.close()
        relCursor.close()
        pgCursor.close()
      }
    }
  }

  def relTypeName(name: String): RelTypeName = RelTypeName(name)(InputPosition.NONE)

  def txToReadTokenContext(tx: InternalTransaction): ReadTokenContext = {
    new ReadTokenContext {
      private val tokenRead = tx.kernelTransaction().tokenRead()
      override def getLabelName(id: Int): String = tokenRead.labelGetName(id)

      override def getOptLabelId(labelName: String): Option[Int] =
        try {
          Some(tokenRead.nodeLabel(labelName))
        } catch {
          case _: LabelNotFoundKernelException => None
        }

      override def getLabelId(labelName: String): Int = tokenRead.nodeLabel(labelName)

      override def getPropertyKeyName(id: Int): String = tokenRead.propertyKeyName(id)

      override def getOptPropertyKeyId(propertyKeyName: String): Option[Int] =
        try {
          Some(tokenRead.propertyKey(propertyKeyName))
        } catch {
          case _: PropertyKeyNotFoundException => None
        }

      override def getPropertyKeyId(propertyKeyName: String): Int = tokenRead.propertyKey(propertyKeyName)

      override def getRelTypeName(id: Int): String = tokenRead.relationshipTypeName(id)

      override def getOptRelTypeId(relType: String): Option[Int] = {
        tokenRead.relationshipType(relType) match {
          case TokenConstants.NO_TOKEN => None
          case t                       => Some(t)
        }
      }

      override def getRelTypeId(relType: String): Int = tokenRead.relationshipType(relType)
    }
  }
}
