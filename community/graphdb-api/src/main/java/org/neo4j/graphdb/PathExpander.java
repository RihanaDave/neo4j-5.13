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
package org.neo4j.graphdb;

import org.neo4j.annotations.api.PublicApi;
import org.neo4j.graphdb.traversal.BranchState;

/**
 * An expander of relationships. It's a flexible way of getting relationships
 * from a {@link Path}. Given a path, which relationships should be expanded
 * from it to traverse further.
 */
@PublicApi
public interface PathExpander<STATE> {
    /**
     * Returns relationships for a {@link Path}, most commonly from the
     * {@link Path#endNode()}.
     *
     * @param path the path to expand (most commonly the end node).
     * @param state the state of this branch in the current traversal.
     * {@link BranchState#getState()} returns the state and
     * {@link BranchState#setState(Object)} optionally sets the state for
     * the children of this branch. If state isn't altered the children
     * of this path will see the state of the parent.
     * @return the relationships to return for the {@code path}.
     */
    ResourceIterable<Relationship> expand(Path path, BranchState<STATE> state);

    /**
     * Returns a new instance with the exact expansion logic, but reversed.
     *
     * @return a reversed {@link PathExpander}.
     */
    PathExpander<STATE> reverse();
}
