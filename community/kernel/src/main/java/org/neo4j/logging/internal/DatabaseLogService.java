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
package org.neo4j.logging.internal;

import org.neo4j.kernel.database.NamedDatabaseId;

public class DatabaseLogService extends AbstractLogService {
    private final DatabaseLogProvider userLogProvider;
    private final DatabaseLogProvider internalLogProvider;

    public DatabaseLogService(NamedDatabaseId namedDatabaseId, LogService delegate) {
        this.userLogProvider = new DatabaseLogProvider(namedDatabaseId, delegate.getUserLogProvider());
        this.internalLogProvider = new DatabaseLogProvider(namedDatabaseId, delegate.getInternalLogProvider());
    }

    @Override
    public DatabaseLogProvider getUserLogProvider() {
        return userLogProvider;
    }

    @Override
    public DatabaseLogProvider getInternalLogProvider() {
        return internalLogProvider;
    }
}
