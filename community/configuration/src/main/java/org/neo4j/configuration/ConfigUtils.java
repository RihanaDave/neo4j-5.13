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
package org.neo4j.configuration;

import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.connectors.HttpsConnector;

public final class ConfigUtils {
    private ConfigUtils() {}

    /**
     * Will disable all configured connectors in the provided config. Useful for tests that does not require connectors to avoid port
     * conflicts and unnecessary resource usage.
     *
     * @param config to disable connectors in.
     */
    public static void disableAllConnectors(Config config) {
        config.set(BoltConnector.enabled, Boolean.FALSE);
        config.set(HttpConnector.enabled, Boolean.FALSE);
        config.set(HttpsConnector.enabled, Boolean.FALSE);
    }
}
