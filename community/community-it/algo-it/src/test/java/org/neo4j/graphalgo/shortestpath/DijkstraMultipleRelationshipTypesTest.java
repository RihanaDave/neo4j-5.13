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
package org.neo4j.graphalgo.shortestpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.Neo4jAlgoTestCase;
import org.neo4j.graphalgo.SimpleGraphBuilder;
import org.neo4j.graphalgo.impl.shortestpath.Dijkstra;
import org.neo4j.graphalgo.impl.util.DoubleAdder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

class DijkstraMultipleRelationshipTypesTest extends Neo4jAlgoTestCase {
    private static Dijkstra<Double> getDijkstra(
            SimpleGraphBuilder graph, Transaction tx, String startNode, String endNode, RelationshipType... relTypes) {
        return new Dijkstra<>(
                0.0,
                graph.getNode(tx, startNode),
                graph.getNode(tx, endNode),
                (relationship, direction) -> 1.0,
                new DoubleAdder(),
                Double::compareTo,
                Direction.BOTH,
                relTypes);
    }

    @Test
    void testRun() {
        try (Transaction transaction = graphDb.beginTx()) {
            graph.setCurrentRelType(MyRelTypes.R1);
            graph.makeEdgeChain(transaction, "a,b,c,d,e");
            graph.setCurrentRelType(MyRelTypes.R2);
            graph.makeEdges(transaction, "a,c"); // first shortcut
            graph.setCurrentRelType(MyRelTypes.R3);
            graph.makeEdges(transaction, "c,e"); // second shortcut
            var dijkstra = getDijkstra(graph, transaction, "a", "e", MyRelTypes.R1);
            assertEquals(4.0, dijkstra.getCost(), 0.0);
            dijkstra = getDijkstra(graph, transaction, "a", "e", MyRelTypes.R1, MyRelTypes.R2);
            assertEquals(3.0, dijkstra.getCost(), 0.0);
            dijkstra = getDijkstra(graph, transaction, "a", "e", MyRelTypes.R1, MyRelTypes.R3);
            assertEquals(3.0, dijkstra.getCost(), 0.0);
            dijkstra = getDijkstra(graph, transaction, "a", "e", MyRelTypes.R1, MyRelTypes.R2, MyRelTypes.R3);
            assertEquals(2.0, dijkstra.getCost(), 0.0);
            transaction.commit();
        }
    }
}
