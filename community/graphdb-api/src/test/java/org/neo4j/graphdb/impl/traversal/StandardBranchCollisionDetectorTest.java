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
package org.neo4j.graphdb.impl.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.TraversalBranch;

class StandardBranchCollisionDetectorTest {
    @Test
    void testFilteredPathEvaluation() {
        final Entity endNode = mock(Node.class);
        final Entity alternativeEndNode = mock(Node.class);
        final Node startNode = mock(Node.class);
        Evaluator evaluator = mock(Evaluator.class);
        TraversalBranch branch = mock(TraversalBranch.class);
        TraversalBranch alternativeBranch = mock(TraversalBranch.class);

        when(branch.iterator()).thenAnswer(new IteratorAnswer(endNode));
        when(alternativeBranch.iterator()).thenAnswer(new IteratorAnswer(alternativeEndNode));
        when(alternativeBranch.startNode()).thenReturn(startNode);
        when(evaluator.evaluate(Mockito.any(Path.class))).thenReturn(Evaluation.INCLUDE_AND_CONTINUE);
        StandardBranchCollisionDetector collisionDetector = new StandardBranchCollisionDetector(
                evaluator, path -> alternativeEndNode.equals(path.endNode()) && startNode.equals(path.startNode()));

        Collection<Path> incoming = collisionDetector.evaluate(branch, Direction.INCOMING);
        Collection<Path> outgoing = collisionDetector.evaluate(branch, Direction.OUTGOING);
        Collection<Path> alternativeIncoming = collisionDetector.evaluate(alternativeBranch, Direction.INCOMING);
        Collection<Path> alternativeOutgoing = collisionDetector.evaluate(alternativeBranch, Direction.OUTGOING);

        assertNull(incoming);
        assertNull(outgoing);
        assertNull(alternativeIncoming);
        assertEquals(1, alternativeOutgoing.size());
    }

    private static class IteratorAnswer implements Answer<Object> {
        private final Entity endNode;

        IteratorAnswer(Entity endNode) {
            this.endNode = endNode;
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            return Arrays.asList(endNode).iterator();
        }
    }
}
