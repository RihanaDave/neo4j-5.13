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
package org.neo4j.kernel.api.index;

import java.io.UncheckedIOException;

/**
 * Minimal index accessor used for dropping failed indexes and provide index configuration.
 */
public interface MinimalIndexAccessor extends IndexConfigProvider {
    MinimalIndexAccessor EMPTY = () -> {};

    /**
     * Deletes this index as well as closes all used external resources.
     * There will not be any interactions after this call.
     *
     * @throws UncheckedIOException if unable to drop index.
     */
    void drop();
}
