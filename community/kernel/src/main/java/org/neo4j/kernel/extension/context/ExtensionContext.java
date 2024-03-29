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
package org.neo4j.kernel.extension.context;

import java.nio.file.Path;
import org.neo4j.common.DependencySatisfier;
import org.neo4j.kernel.impl.factory.DbmsInfo;

/**
 * Context that provide information about outside environment into the extension.
 */
public interface ExtensionContext {

    DbmsInfo dbmsInfo();

    DependencySatisfier dependencySatisfier();

    /**
     * Context root directory.
     * Depending from type of context directory can be equal to store root directory or database root directory.
     * @return context root directory.
     */
    Path directory();
}
