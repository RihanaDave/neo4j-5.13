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
package org.neo4j.dbms.systemgraph;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DEFAULT_NAMESPACE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.configuration.helpers.RemoteUri;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.database.DatabaseReferenceImpl;
import org.neo4j.kernel.database.NormalizedDatabaseName;

public class CommunityTopologyGraphDbmsModelIT extends BaseTopologyGraphDbmsModelIT {

    private CommunityTopologyGraphDbmsModel dbmsModel;

    @Override
    protected void createModel(Transaction tx) {
        dbmsModel = new CommunityTopologyGraphDbmsModel(tx);
    }

    private static NormalizedDatabaseName name(String name) {
        return new NormalizedDatabaseName(name);
    }

    @Test
    void canReturnAllInternalDatabaseReferences() {
        // given
        var fooDb = newDatabase(b -> b.withDatabase("foo"));
        var barDb = newDatabase(b -> b.withDatabase("bar"));
        createInternalReferenceForDatabase(tx, "fooAlias", false, fooDb);
        createInternalReferenceForDatabase(tx, "fooOtherAlias", false, fooDb);
        createInternalReferenceForDatabase(tx, "barAlias", false, barDb);

        var expected = Set.of(
                new DatabaseReferenceImpl.Internal(name("foo"), fooDb, true),
                new DatabaseReferenceImpl.Internal(name("bar"), barDb, true),
                new DatabaseReferenceImpl.Internal(name("fooAlias"), fooDb, false),
                new DatabaseReferenceImpl.Internal(name("fooOtherAlias"), fooDb, false),
                new DatabaseReferenceImpl.Internal(name("barAlias"), barDb, false));

        // when
        var aliases = dbmsModel.getAllInternalDatabaseReferences();

        // then
        assertThat(aliases).isEqualTo(expected);
    }

    @Test
    void canReturnAllExternalDatabaseReferences() {
        // given
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var fooId = randomUUID();
        var fooOtherId = randomUUID();
        var barId = randomUUID();
        createExternalReferenceForDatabase(tx, "fooAlias", "foo", remoteNeo4j, fooId);
        createExternalReferenceForDatabase(tx, "fooOtherAlias", "foo", remoteNeo4j, fooOtherId);
        createExternalReferenceForDatabase(tx, "barAlias", "bar", remoteNeo4j, barId);

        var expected = Set.of(
                new DatabaseReferenceImpl.External(name("foo"), name("fooAlias"), remoteNeo4j, fooId),
                new DatabaseReferenceImpl.External(name("foo"), name("fooOtherAlias"), remoteNeo4j, fooOtherId),
                new DatabaseReferenceImpl.External(name("bar"), name("barAlias"), remoteNeo4j, barId));

        // when
        var aliases = dbmsModel.getAllExternalDatabaseReferences();

        // then
        assertThat(aliases).isEqualTo(expected);
    }

    @Test
    void canReturnAllCompositeDatabaseReferences() {
        // given
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var remAliasId1 = randomUUID();
        var remAliasId2 = randomUUID();
        var remAliasId3 = randomUUID();
        var remAliasId4 = randomUUID();
        var remAliasId5 = randomUUID();
        var locDb = newDatabase(b -> b.withDatabase("loc"));
        createInternalReferenceForDatabase(tx, "locAlias", false, locDb);
        createExternalReferenceForDatabase(tx, "remAlias", "rem1", remoteNeo4j, remAliasId1);
        var compDb1 = newDatabase(b -> b.withDatabase("compDb1").asVirtual());
        var compDb1Name = name(compDb1.name());
        createInternalReferenceForDatabase(tx, compDb1.name(), true, compDb1);
        createInternalReferenceForDatabase(tx, compDb1.name(), "locAlias", false, locDb);
        createExternalReferenceForDatabase(tx, compDb1.name(), "remAlias", "rem2", remoteNeo4j, remAliasId2);
        createExternalReferenceForDatabase(tx, compDb1.name(), "remAlias2", "rem3", remoteNeo4j, remAliasId3);
        var compDb2 = newDatabase(b -> b.withDatabase("compDb2").asVirtual());
        var compDb2Name = name(compDb2.name());
        createInternalReferenceForDatabase(tx, compDb2.name(), true, compDb2);
        createInternalReferenceForDatabase(tx, compDb2.name(), "locAlias", false, locDb);
        createExternalReferenceForDatabase(tx, compDb2.name(), "remAlias", "rem4", remoteNeo4j, remAliasId4);
        createExternalReferenceForDatabase(tx, compDb2.name(), "remAlias3", "rem5", remoteNeo4j, remAliasId5);

        // then
        assertThat(dbmsModel.getAllCompositeDatabaseReferences())
                .isEqualTo(Set.of(
                        new DatabaseReferenceImpl.Composite(
                                compDb1Name,
                                compDb1,
                                Set.of(
                                        new DatabaseReferenceImpl.Internal(name("locAlias"), compDb1Name, locDb, false),
                                        new DatabaseReferenceImpl.External(
                                                name("rem2"), name("remAlias"), compDb1Name, remoteNeo4j, remAliasId2),
                                        new DatabaseReferenceImpl.External(
                                                name("rem3"),
                                                name("remAlias2"),
                                                compDb1Name,
                                                remoteNeo4j,
                                                remAliasId3))),
                        new DatabaseReferenceImpl.Composite(
                                compDb2Name,
                                compDb2,
                                Set.of(
                                        new DatabaseReferenceImpl.Internal(name("locAlias"), compDb2Name, locDb, false),
                                        new DatabaseReferenceImpl.External(
                                                name("rem4"), name("remAlias"), compDb2Name, remoteNeo4j, remAliasId4),
                                        new DatabaseReferenceImpl.External(
                                                name("rem5"),
                                                name("remAlias3"),
                                                compDb2Name,
                                                remoteNeo4j,
                                                remAliasId5)))));

        assertThat(dbmsModel.getAllInternalDatabaseReferences())
                .isEqualTo(Set.of(
                        new DatabaseReferenceImpl.Internal(name("loc"), locDb, true),
                        new DatabaseReferenceImpl.Internal(name("locAlias"), locDb, false)));

        assertThat(dbmsModel.getAllExternalDatabaseReferences())
                .isEqualTo(Set.of(
                        new DatabaseReferenceImpl.External(name("rem1"), name("remAlias"), remoteNeo4j, remAliasId1)));
    }

    @Test
    void canReturnAllDatabaseReferences() {
        // given
        var fooDb = newDatabase(b -> b.withDatabase("foo"));
        createInternalReferenceForDatabase(tx, "fooAlias", false, fooDb);
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var barId = UUID.randomUUID();
        var barId2 = UUID.randomUUID();
        createExternalReferenceForDatabase(tx, "bar", "foo", remoteNeo4j, barId);
        var compDb1 = newDatabase(b -> b.withDatabase("compDb1").asVirtual());
        var compDb1Name = name(compDb1.name());
        createInternalReferenceForDatabase(tx, compDb1.name(), true, compDb1);
        createInternalReferenceForDatabase(tx, compDb1.name(), "locAlias", false, fooDb);
        createExternalReferenceForDatabase(tx, compDb1.name(), "remAlias", "rem", remoteNeo4j, barId2);

        var expected = Set.of(
                new DatabaseReferenceImpl.External(name("foo"), name("bar"), remoteNeo4j, barId),
                new DatabaseReferenceImpl.Internal(name("foo"), fooDb, true),
                new DatabaseReferenceImpl.Internal(name("fooAlias"), fooDb, false),
                new DatabaseReferenceImpl.Composite(
                        name(compDb1.name()),
                        compDb1,
                        Set.of(
                                new DatabaseReferenceImpl.Internal(name("locAlias"), compDb1Name, fooDb, false),
                                new DatabaseReferenceImpl.External(
                                        name("rem"), name("remAlias"), compDb1Name, remoteNeo4j, barId2))));

        // when
        var aliases = dbmsModel.getAllDatabaseReferences();

        // then
        assertThat(aliases).isEqualTo(expected);
    }

    @Test
    void shouldReturnEmptyForDriverSettingsIfNoneExist() {
        // given
        var aliasName = "fooAlias";
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        createExternalReferenceForDatabase(tx, aliasName, "foo", remoteNeo4j, randomUUID());

        // when
        var result = dbmsModel.getDriverSettings(aliasName, DEFAULT_NAMESPACE);

        // then
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("driverSettings")
    void canReturnDriverSettingsForExternalDatabaseReference(DriverSettings driverSettings) {
        // given
        var aliasName = "fooAlias";
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var fooId = randomUUID();
        var aliasNode = createExternalReferenceForDatabase(tx, aliasName, "foo", remoteNeo4j, fooId);

        createDriverSettingsForExternalAlias(tx, aliasNode, driverSettings);

        // when
        var result = dbmsModel.getDriverSettings(aliasName, DEFAULT_NAMESPACE);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(driverSettings);
    }

    @ParameterizedTest
    @MethodSource("aliasProperties")
    void canReturnPropertiesForExternalDatabaseReference(Map<String, Object> properties) {
        // given
        var aliasName = "fooAlias";
        var aliasNamespace = "composite";
        var remoteAddress = new SocketAddress("my.neo4j.com", 7687);
        var remoteNeo4j = new RemoteUri("neo4j", List.of(remoteAddress), null);
        var fooId = randomUUID();
        var aliasNode = createExternalReferenceForDatabase(tx, aliasNamespace, aliasName, "foo", remoteNeo4j, fooId);

        createPropertiesForAlias(tx, aliasNode, properties);

        // when
        var result = dbmsModel.getAliasProperties(aliasName, aliasNamespace);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(properties);
    }

    @ParameterizedTest
    @MethodSource("aliasProperties")
    void canReturnPropertiesForInternalDatabaseReference(Map<String, Object> properties) {
        // given
        var aliasName = "fooAlias";
        var aliasNamespace = "composite";
        var locDb = newDatabase(b -> b.withDatabase("loc"));
        var aliasNode = createInternalReferenceForDatabase(tx, aliasNamespace, aliasName, false, locDb);

        createPropertiesForAlias(tx, aliasNode, properties);

        // when
        var result = dbmsModel.getAliasProperties(aliasName, aliasNamespace);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(properties);
    }
}
