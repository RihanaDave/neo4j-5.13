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
package org.neo4j.graphdb.config;

import org.neo4j.annotations.api.PublicApi;

/**
 * Provide the basic operation that one could perform on a set of configurations.
 */
@PublicApi
public interface Configuration {
    /**
     * Retrieve the value of a configuration {@link Setting}.
     *
     * @param setting The configuration property
     * @param <T> The type of the configuration property
     * @return The value of the configuration property if the property is found, otherwise, return the default value
     * of the given property.
     */
    <T> T get(Setting<T> setting);

    /**
     * Empty configuration without any settings.
     */
    Configuration EMPTY = new Configuration() {
        @Override
        public <T> T get(Setting<T> setting) {
            return null;
        }
    };
}
