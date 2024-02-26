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
package org.neo4j.kernel.api.index;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.graphdb.schema.IndexSettingUtil;
import org.neo4j.internal.schema.IndexConfig;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.internal.schema.SchemaDescriptors;
import org.neo4j.kernel.api.impl.schema.vector.VectorSimilarityFunction;
import org.neo4j.kernel.api.impl.schema.vector.VectorUtils;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.Tags;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;

@ImpermanentDbmsExtension
public class VectorIndexCreationTest {
    private static final Label LABEL = Tags.Suppliers.UUID.LABEL.get();
    private static final RelationshipType REL_TYPE = Tags.Suppliers.UUID.RELATIONSHIP_TYPE.get();
    private static final List<String> PROP_KEYS = Tags.Suppliers.UUID.PROPERTY_KEY.get(2);

    @Inject
    private GraphDatabaseAPI db;

    private int labelId;
    private int relTypeId;
    private int[] propKeyIds;

    @BeforeEach
    void setup() throws Exception {
        try (final var tx = db.beginTx()) {
            final var ktx = ((InternalTransaction) tx).kernelTransaction();
            labelId = Tags.Factories.LABEL.getId(ktx, LABEL);
            relTypeId = Tags.Factories.RELATIONSHIP_TYPE.getId(ktx, REL_TYPE);
            propKeyIds = Tags.Factories.PROPERTY_KEY.getIds(ktx, PROP_KEYS).stream()
                    .mapToInt(k -> k)
                    .toArray();
            tx.commit();
        }
    }

    @Test
    void shouldAcceptNodeVectorIndex() {
        assertThatCode(() -> createVectorIndex(SchemaDescriptors.forLabel(labelId, propKeyIds[0]), defaultConfig()))
                .doesNotThrowAnyException();
        assertThatCode(() -> createNodeVectorIndexCoreAPI(PROP_KEYS.get(1), defaultSettings()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRelationshipVectorIndex() {
        assertUnsupportedRelationshipIndex(
                () -> createVectorIndex(SchemaDescriptors.forRelType(relTypeId, propKeyIds[0]), defaultConfig()));
        assertUnsupportedRelationshipIndex(() -> createRelVectorIndexCoreAPI(PROP_KEYS.get(1), defaultSettings()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 738, 1024, 1408, 1536, 2048, VectorUtils.MAX_DIMENSIONS})
    void shouldAcceptValidDimensions(int dimensions) {
        assertThatCode(() -> createVectorIndex(
                        SchemaDescriptors.forLabel(labelId, propKeyIds[0]),
                        defaultConfigWith(IndexSetting.vector_Dimensions(), dimensions)))
                .doesNotThrowAnyException();

        assertThatCode(() -> createNodeVectorIndexCoreAPI(
                        PROP_KEYS.get(1), defaultSettingsWith(IndexSetting.vector_Dimensions(), dimensions)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void shouldRejectIllegalDimensions(int dimensions) {
        assertIllegalDimensions(() -> createVectorIndex(
                SchemaDescriptors.forLabel(labelId, propKeyIds[0]),
                defaultConfigWith(IndexSetting.vector_Dimensions(), dimensions)));

        assertIllegalDimensions(() -> createNodeVectorIndexCoreAPI(
                PROP_KEYS.get(1), defaultSettingsWith(IndexSetting.vector_Dimensions(), dimensions)));
    }

    @Test
    void shouldRejectUnsupportedDimensions() {
        final var dimensions = VectorUtils.MAX_DIMENSIONS + 1;

        assertUnsupportedDimensions(() -> createVectorIndex(
                SchemaDescriptors.forLabel(labelId, propKeyIds[0]),
                defaultConfigWith(IndexSetting.vector_Dimensions(), dimensions)));

        assertUnsupportedDimensions(() -> createNodeVectorIndexCoreAPI(
                PROP_KEYS.get(1), defaultSettingsWith(IndexSetting.vector_Dimensions(), dimensions)));
    }

    @ParameterizedTest
    @EnumSource
    void shouldAcceptValidSimilarityFunction(VectorSimilarityFunction similarityFunction) {
        final var validSimilarityFunctionName = similarityFunction.name();

        assertThatCode(() -> createVectorIndex(
                        SchemaDescriptors.forLabel(labelId, propKeyIds[0]),
                        defaultConfigWith(IndexSetting.vector_Similarity_Function(), validSimilarityFunctionName)))
                .doesNotThrowAnyException();

        assertThatCode(() -> createNodeVectorIndexCoreAPI(
                        PROP_KEYS.get(1),
                        defaultSettingsWith(IndexSetting.vector_Similarity_Function(), validSimilarityFunctionName)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectIllegalSimilarityFunction() {
        final var invalidSimilarityFunctionName = "ClearlyThisIsNotASimilarityFunction";

        assertIllegalSimilarityFunction(() -> createVectorIndex(
                SchemaDescriptors.forLabel(labelId, propKeyIds[0]),
                defaultConfigWith(IndexSetting.vector_Similarity_Function(), invalidSimilarityFunctionName)));

        assertIllegalSimilarityFunction(() -> createNodeVectorIndexCoreAPI(
                PROP_KEYS.get(1),
                defaultSettingsWith(IndexSetting.vector_Similarity_Function(), invalidSimilarityFunctionName)));
    }

    @Test
    void shouldRejectCompositeKeys() {
        assertUnsupportedComposite(
                () -> createVectorIndex(SchemaDescriptors.forLabel(labelId, propKeyIds), defaultConfig()));
        assertUnsupportedComposite(
                () -> createVectorIndex(SchemaDescriptors.forRelType(relTypeId, propKeyIds), defaultConfig()));
        assertUnsupportedComposite(() -> createNodeVectorIndexCoreAPI(PROP_KEYS, defaultSettings()));
        assertUnsupportedComposite(() -> createRelVectorIndexCoreAPI(PROP_KEYS, defaultSettings()));
    }

    private void createVectorIndex(SchemaDescriptor schema, IndexConfig config) throws Exception {
        try (final var tx = db.beginTx()) {
            final var ktx = ((InternalTransaction) tx).kernelTransaction();
            final var prototype = IndexPrototype.forSchema(schema)
                    .withIndexType(IndexType.VECTOR)
                    .withIndexConfig(config);
            ktx.schemaWrite().indexCreate(prototype);
            tx.commit();
        }
    }

    private void createNodeVectorIndexCoreAPI(String propKey, Map<IndexSetting, Object> settings) {
        createNodeVectorIndexCoreAPI(List.of(propKey), settings);
    }

    private void createNodeVectorIndexCoreAPI(List<String> propKeys, Map<IndexSetting, Object> settings) {
        try (final var tx = db.beginTx()) {
            var creator = tx.schema()
                    .indexFor(LABEL)
                    .withIndexType(IndexType.VECTOR.toPublicApi())
                    .withIndexConfiguration(settings);
            for (final var propKey : propKeys) {
                creator = creator.on(propKey);
            }
            creator.create();
            tx.commit();
        }
    }

    private void createRelVectorIndexCoreAPI(String propKey, Map<IndexSetting, Object> settings) {
        createRelVectorIndexCoreAPI(List.of(propKey), settings);
    }

    private void createRelVectorIndexCoreAPI(List<String> propKeys, Map<IndexSetting, Object> settings) {
        try (final var tx = db.beginTx()) {
            var creator = tx.schema()
                    .indexFor(REL_TYPE)
                    .withIndexType(IndexType.VECTOR.toPublicApi())
                    .withIndexConfiguration(settings);
            for (final var propKey : propKeys) {
                creator = creator.on(propKey);
            }
            creator.create();
            tx.commit();
        }
    }

    private static IndexConfig defaultConfig() {
        return IndexSettingUtil.defaultConfigForTest(IndexType.VECTOR.toPublicApi());
    }

    private static IndexConfig defaultConfigWith(IndexSetting setting, Object value) {
        return configFrom(defaultSettingsWith(setting, value));
    }

    private static IndexConfig configFrom(Map<IndexSetting, Object> settings) {
        return IndexSettingUtil.toIndexConfigFromIndexSettingObjectMap(settings);
    }

    private static Map<IndexSetting, Object> defaultSettings() {
        return IndexSettingUtil.defaultSettingsForTesting(IndexType.VECTOR.toPublicApi());
    }

    private static Map<IndexSetting, Object> defaultSettingsWith(IndexSetting setting, Object value) {
        final var settings = new HashMap<>(defaultSettings());
        settings.put(setting, value);
        return Collections.unmodifiableMap(settings);
    }

    private static void assertUnsupportedRelationshipIndex(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContainingAll(
                        "Relationship indexes are not supported for", IndexType.VECTOR.name(), "index type");
    }

    private static void assertIllegalDimensions(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalArgumentException.class)
                .cause()
                .hasMessageContainingAll(
                        IndexSetting.vector_Dimensions().getSettingName(), "is expected to be positive");
    }

    private static void assertUnsupportedDimensions(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContainingAll(IndexSetting.vector_Dimensions().getSettingName(), "set greater than");
    }

    private static void assertIllegalSimilarityFunction(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalArgumentException.class)
                .cause()
                .hasMessageContainingAll(
                        "is an unsupported vector similarity function",
                        "Supported",
                        VectorSimilarityFunction.SUPPORTED.toString());
    }

    private static void assertUnsupportedComposite(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContainingAll(
                        "Composite indexes are not supported for", IndexType.VECTOR.name(), "index type");
    }
}
