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
package org.neo4j.graphalgo.impl.path;

import static org.neo4j.graphdb.traversal.Evaluators.atDepth;
import static org.neo4j.graphdb.traversal.Evaluators.toDepth;

import org.neo4j.graphalgo.EvaluationContext;
import org.neo4j.graphalgo.impl.util.LiteDepthFirstSelector;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * Tries to find paths in a graph from a start node to an end node where the
 * length of found paths must be of a certain length. It also detects
 * "super nodes", i.e. nodes which have many relationships and only iterates
 * over such super nodes' relationships up to a supplied threshold. When that
 * threshold is reached such nodes are considered super nodes and are put on a
 * queue for later traversal. This makes it possible to find paths w/o having to
 * traverse heavy super nodes.
 */
public class ExactDepthPathFinder extends TraversalPathFinder {
    private final EvaluationContext context;
    private final PathExpander expander;
    private final int onDepth;
    private final int startThreshold;
    private final Uniqueness uniqueness;

    public ExactDepthPathFinder(
            EvaluationContext context, PathExpander expander, int onDepth, int startThreshold, boolean allowLoops) {
        this.context = context;
        this.expander = expander;
        this.onDepth = onDepth;
        this.startThreshold = startThreshold;
        this.uniqueness = allowLoops ? Uniqueness.RELATIONSHIP_GLOBAL : Uniqueness.NODE_PATH;
    }

    @Override
    protected Traverser instantiateTraverser(Node start, Node end) {
        var transaction = context.transaction();
        TraversalDescription side = transaction
                .traversalDescription()
                .breadthFirst()
                .uniqueness(uniqueness)
                .order((startSource, expander) -> new LiteDepthFirstSelector(startSource, startThreshold, expander));
        return transaction
                .bidirectionalTraversalDescription()
                .startSide(side.expand(expander).evaluator(toDepth(onDepth / 2)))
                .endSide(side.expand(expander.reverse()).evaluator(toDepth(onDepth - onDepth / 2)))
                .collisionEvaluator(atDepth(onDepth))
                // TODO Level side selector will make the traversal return wrong result, why?
                //                .sideSelector( SideSelectorPolicies.LEVEL, onDepth )
                .traverse(start, end);
    }
}
