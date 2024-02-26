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
package org.neo4j.internal.kernel.api.helpers.traversal.productgraph;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import org.neo4j.graphdb.Direction;
import org.neo4j.internal.kernel.api.RelationshipTraversalCursor;

public final class RelationshipExpansion implements Transition {
    private final Predicate<RelationshipTraversalCursor> relPredicate;
    private final int[] types;
    private final Direction direction;
    private final State.VarName relName;
    private final LongPredicate nodePredicate;
    private State targetState;

    public RelationshipExpansion(
            Predicate<RelationshipTraversalCursor> relPredicate,
            int[] types,
            Direction direction,
            State.VarName relName,
            LongPredicate nodePredicate,
            State targetState) {
        this.relPredicate = relPredicate;
        this.types = types;
        this.direction = direction;
        this.relName = relName;
        this.nodePredicate = nodePredicate;
        this.targetState = targetState;
    }

    public boolean testRelationship(RelationshipTraversalCursor cursor) {
        return relPredicate.test(cursor);
    }

    public boolean testNode(long node) {
        return nodePredicate.test(node);
    }

    public int[] types() {
        return types;
    }

    public Direction direction() {
        return direction;
    }

    public State.VarName relName() {
        return relName;
    }

    @Override
    public State targetState() {
        return targetState;
    }

    @Override
    public void setTargetState(State state) {
        assert targetState == null
                : "Shouldn't set target state more than once. The targetState field is only mutable to support delayed initialization which is require when there are cycles in the NFA";
        this.targetState = state;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (RelationshipExpansion) obj;
        return Objects.equals(this.relPredicate, that.relPredicate)
                && Arrays.equals(this.types, that.types)
                && Objects.equals(this.direction, that.direction)
                && Objects.equals(this.relName, that.relName)
                && Objects.equals(this.nodePredicate, that.nodePredicate)
                && Objects.equals(this.targetState, that.targetState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relPredicate, Arrays.hashCode(types), direction, relName, nodePredicate, targetState);
    }

    @Override
    public String toString() {
        return "RelationshipExpansion[" + "relPredicate="
                + relPredicate + ", " + "types="
                + Arrays.toString(types) + ", " + "direction="
                + direction + ", " + "relName="
                + relName + ", " + "nodePredicate="
                + nodePredicate + ", " + "targetState="
                + targetState + ']';
    }
}
