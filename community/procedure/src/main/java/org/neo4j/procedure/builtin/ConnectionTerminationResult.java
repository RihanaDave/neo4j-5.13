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
package org.neo4j.procedure.builtin;

public class ConnectionTerminationResult {
    private static final String SUCCESS_MESSAGE = "Connection found";

    public final String connectionId;
    public final String username;
    public final String message;

    ConnectionTerminationResult(String connectionId, String username) {
        this(connectionId, username, SUCCESS_MESSAGE);
    }

    ConnectionTerminationResult(String connectionId, String username, String message) {
        this.connectionId = connectionId;
        this.username = username;
        this.message = message;
    }
}
