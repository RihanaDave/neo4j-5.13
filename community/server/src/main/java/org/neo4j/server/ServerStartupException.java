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
package org.neo4j.server;

import static java.lang.String.format;

import org.neo4j.logging.InternalLog;

public class ServerStartupException extends RuntimeException {
    public ServerStartupException(String message, Throwable t) {
        super(message, t);
    }

    public ServerStartupException(String message) {
        super(message);
    }

    public void describeTo(InternalLog log) {
        // By default, log the full error. The intention is that sub classes can override this and
        // specify less extreme logging options.
        log.error(format("Failed to start Neo4j: %s", getMessage()), this);
    }
}
