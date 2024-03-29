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
package org.neo4j.test.extension;

import static org.neo4j.configuration.GraphDatabaseSettings.initial_default_database;

import org.junit.jupiter.api.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

@DbmsExtension(configurationCallback = "configureGlobal")
class DbmsExtensionConfigurationTest {
    @Inject
    private DatabaseManagementService dbms;

    @ExtensionCallback
    static void configureGlobal(TestDatabaseManagementServiceBuilder builder) {
        builder.setConfig(initial_default_database, "global");
    }

    @ExtensionCallback
    static void configureLocal(TestDatabaseManagementServiceBuilder builder) {
        builder.setConfig(initial_default_database, "local");
    }

    @Test
    void globalConfig() {
        dbms.database("global");
    }

    @Test
    @DbmsExtension(configurationCallback = "configureLocal")
    void localConfig() {
        dbms.database("local");
    }

    @Test
    @DbmsExtension // Should override "global" with default
    void defaultConfig() {
        dbms.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    }
}
