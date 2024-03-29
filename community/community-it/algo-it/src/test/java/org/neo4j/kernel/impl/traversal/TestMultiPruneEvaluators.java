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
package org.neo4j.kernel.impl.traversal;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.graphdb.traversal.Evaluators.toDepth;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.internal.helpers.collection.Iterables;

class TestMultiPruneEvaluators extends TraversalTestBase {
    @BeforeEach
    void setupGraph() {
        createGraph(
                "a to b", "a to c", "a to d", "a to e", "b to f", "b to g", "b to h", "c to i", "d to j", "d to k",
                "d to l", "e to m", "e to n", "k to o", "k to p", "k to q", "k to r");
    }

    @Test
    void testMaxDepthAndCustomPruneEvaluatorCombined() {
        Evaluator lessThanThreeRels = path -> Iterables.count(path.endNode().getRelationships(Direction.OUTGOING)) < 3
                ? Evaluation.INCLUDE_AND_PRUNE
                : Evaluation.INCLUDE_AND_CONTINUE;

        Set<String> expectedNodes = new HashSet<>(asList("a", "b", "c", "d", "e"));
        try (Transaction tx = beginTx()) {
            TraversalDescription description = tx.traversalDescription()
                    .evaluator(Evaluators.all())
                    .evaluator(toDepth(1))
                    .evaluator(lessThanThreeRels);
            for (Path position : description.traverse(tx.getNodeById(node("a").getId()))) {
                String name = (String) position.endNode().getProperty("name");
                assertTrue(expectedNodes.remove(name), name + " shouldn't have been returned");
            }
            tx.commit();
        }
        assertTrue(expectedNodes.isEmpty());
    }
}
