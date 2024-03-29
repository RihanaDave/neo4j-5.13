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
package org.neo4j.internal.helpers.collection;

/**
 * A visitor to internalize iteration.
 *
 * @param <E> the element type the visitor accepts.
 * @param <FAILURE> the type of exception the visitor might throw
 */
@FunctionalInterface
public interface Visitor<E, FAILURE extends Exception> {
    /**
     * Invoked for each element in a collection. Return <code>true</code> to
     * terminate the iteration, <code>false</code> to continue.
     *
     * @param element an element from the collection.
     * @return <code>true</code> to terminate the iteration, <code>false</code>
     *         to continue.
     * @throws FAILURE exception thrown by the visitor
     */
    boolean visit(E element) throws FAILURE;
}
