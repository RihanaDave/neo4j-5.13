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

/**
 * This exception will be thrown if a request is made to a node, relationship or
 * property that does not exist. As an example, using
 * {@link Transaction#getNodeByElementId} passing in an id that does not exist
 * will cause this exception to be thrown.
 * {@link Entity#getProperty(String)} will also throw this exception
 * if the given key does not exist.
 * <p>
 * Another scenario involves multiple concurrent transactions which obtain a reference to the same node or
 * relationship, which is then deleted by one of the transactions. If the deleting transaction commits, then invoking
 * any node or relationship methods within any of the remaining open transactions will cause this exception to be
 * thrown.
 *
 * @see GraphDatabaseService
 */
@PublicApi
public class NotFoundException extends RuntimeException {
    public NotFoundException() {
        super();
    }

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotFoundException(Throwable cause) {
        super(cause);
    }
}
