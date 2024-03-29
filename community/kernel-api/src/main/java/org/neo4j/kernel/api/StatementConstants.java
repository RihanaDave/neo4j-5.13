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
package org.neo4j.kernel.api;

import org.neo4j.token.api.TokenConstants;

public final class StatementConstants {
    public static final int NO_SUCH_RELATIONSHIP_TYPE = TokenConstants.ANY_RELATIONSHIP_TYPE;
    public static final int NO_SUCH_LABEL = TokenConstants.ANY_LABEL;
    public static final int NO_SUCH_PROPERTY_KEY = TokenConstants.ANY_PROPERTY_KEY;
    public static final long NO_SUCH_NODE = -1;
    public static final long NO_SUCH_RELATIONSHIP = -1;
    public static final long NO_SUCH_ENTITY = -1;

    private StatementConstants() {
        throw new UnsupportedOperationException();
    }
}
