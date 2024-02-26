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
package org.neo4j.kernel.impl.storemigration;

import java.io.IOException;
import java.util.OptionalLong;
import java.util.function.Function;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.neo4j.common.EntityType;
import org.neo4j.configuration.Config;
import org.neo4j.exceptions.KernelException;
import org.neo4j.exceptions.UnderlyingStorageException;
import org.neo4j.internal.kernel.api.TokenRead;
import org.neo4j.internal.schema.ConstraintDescriptor;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.internal.schema.SchemaDescriptors;
import org.neo4j.internal.schema.constraints.ConstraintDescriptorFactory;
import org.neo4j.internal.schema.constraints.IndexBackedConstraintDescriptor;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.context.CursorContextFactory;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.database.DatabaseTracers;
import org.neo4j.kernel.impl.newapi.ReadOnlyTokenRead;
import org.neo4j.kernel.impl.transaction.log.LogTailMetadata;
import org.neo4j.kernel.recovery.LogTailExtractor;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.storageengine.api.StorageEngineFactory;
import org.neo4j.storageengine.migration.SchemaRuleMigrationAccessExtended;
import org.neo4j.token.TokenHolders;

public class SchemaMigrator {

    private SchemaMigrator() {}

    public static void migrateSchemaRules(
            StorageEngineFactory fromStorage,
            StorageEngineFactory toStorage,
            FileSystemAbstraction fs,
            PageCache pageCache,
            PageCacheTracer pageCacheTracer,
            Config config,
            DatabaseLayout from,
            DatabaseLayout toLayout,
            CursorContextFactory contextFactory)
            throws IOException, KernelException {
        // Need to start the stores with the correct logTail since some stores depend on tx-id.
        LogTailExtractor logTailExtractor =
                new LogTailExtractor(fs, pageCache, config, toStorage, DatabaseTracers.EMPTY);
        LogTailMetadata logTail = logTailExtractor.getTailMetadata(toLayout, EmptyMemoryTracker.INSTANCE);

        var tokenHolders =
                fromStorage.loadReadOnlyTokens(fs, from, config, pageCache, pageCacheTracer, true, contextFactory);

        try (SchemaRuleMigrationAccessExtended schemaRuleMigrationAccess = toStorage.schemaRuleMigrationAccess(
                fs,
                pageCache,
                pageCacheTracer,
                config,
                toLayout,
                contextFactory,
                EmptyMemoryTracker.INSTANCE,
                logTail)) {
            TokenRead tokenRead = new ReadOnlyTokenRead(tokenHolders);

            LongObjectHashMap<IndexToConnect> indexesToConnect = new LongObjectHashMap<>();
            LongObjectHashMap<ConstraintToConnect> constraintsToConnect = new LongObjectHashMap<>();
            // Write the rules to the new store.
            //  - Translating the tokens since their ids might be different
            for (var schemaRule : fromStorage.loadSchemaRules(
                    fs, pageCache, pageCacheTracer, config, from, true, Function.identity(), contextFactory)) {
                if (schemaRule instanceof IndexDescriptor indexDescriptor) {
                    if (indexDescriptor.isTokenIndex()) {
                        // Skip since they have already been created by the copy operation
                        continue;
                    }
                    SchemaDescriptor schema = translateToNewSchema(
                            indexDescriptor.schema(), tokenRead, schemaRuleMigrationAccess.tokenHolders());

                    IndexPrototype newPrototype = indexDescriptor.isUnique()
                            ? IndexPrototype.uniqueForSchema(schema, indexDescriptor.getIndexProvider())
                            : IndexPrototype.forSchema(schema, indexDescriptor.getIndexProvider());
                    newPrototype = newPrototype
                            .withName(indexDescriptor.getName())
                            .withIndexType(indexDescriptor.getIndexType())
                            .withIndexConfig(indexDescriptor.getIndexConfig());

                    if (indexDescriptor.isUnique()) {
                        // Handle constraint indexes later
                        indexesToConnect.put(
                                indexDescriptor.getId(),
                                new IndexToConnect(
                                        indexDescriptor.getId(),
                                        indexDescriptor.getOwningConstraintId(),
                                        newPrototype));
                    } else {
                        IndexDescriptor newDescriptor = newPrototype.materialise(schemaRuleMigrationAccess.nextId());
                        schemaRuleMigrationAccess.writeSchemaRule(newDescriptor);
                    }
                } else if (schemaRule instanceof ConstraintDescriptor constraintDescriptor) {
                    SchemaDescriptor schema = translateToNewSchema(
                            constraintDescriptor.schema(), tokenRead, schemaRuleMigrationAccess.tokenHolders());
                    ConstraintDescriptor descriptor =
                            switch (constraintDescriptor.type()) {
                                case UNIQUE -> {
                                    IndexBackedConstraintDescriptor indexBacked =
                                            constraintDescriptor.asIndexBackedConstraint();
                                    yield ConstraintDescriptorFactory.uniqueForSchema(schema, indexBacked.indexType());
                                }
                                case EXISTS -> ConstraintDescriptorFactory.existsForSchema(schema);
                                case UNIQUE_EXISTS -> {
                                    IndexBackedConstraintDescriptor indexBacked =
                                            constraintDescriptor.asIndexBackedConstraint();
                                    yield ConstraintDescriptorFactory.keyForSchema(schema, indexBacked.indexType());
                                }
                                case PROPERTY_TYPE -> ConstraintDescriptorFactory.typeForSchema(
                                        schema,
                                        constraintDescriptor
                                                .asPropertyTypeConstraint()
                                                .propertyType());
                            };
                    descriptor = descriptor.withName(constraintDescriptor.getName());

                    if (descriptor.isIndexBackedConstraint()) {
                        // Handle index-backed constraints later
                        constraintsToConnect.put(
                                constraintDescriptor.getId(),
                                new ConstraintToConnect(
                                        constraintDescriptor.getId(),
                                        constraintDescriptor
                                                .asIndexBackedConstraint()
                                                .ownedIndexId(),
                                        descriptor));
                    } else {
                        descriptor = descriptor.withId(schemaRuleMigrationAccess.nextId());
                        schemaRuleMigrationAccess.writeSchemaRule(descriptor);
                    }
                }
            }

            // Time to handle constraint/index connections
            for (ConstraintToConnect constraintToConnect : constraintsToConnect.values()) {
                IndexToConnect indexToConnect = indexesToConnect.remove(constraintToConnect.indexId);
                if (indexToConnect == null
                        || (indexToConnect.oldConstraintId.isPresent()
                                && indexToConnect.oldConstraintId.getAsLong() != constraintToConnect.oldId)) {
                    throw new UnderlyingStorageException(
                            "Encountered an inconsistent schema store - can not migrate. Affected rules have id "
                                    + constraintToConnect.oldId
                                    + (indexToConnect != null ? " and " + indexToConnect.oldId : ""));
                }

                long newIndexId = schemaRuleMigrationAccess.nextId();
                long newConstraintId = schemaRuleMigrationAccess.nextId();
                schemaRuleMigrationAccess.writeSchemaRule(
                        indexToConnect.prototype.materialise(newIndexId).withOwningConstraintId(newConstraintId));
                schemaRuleMigrationAccess.writeSchemaRule(
                        constraintToConnect.prototype.withId(newConstraintId).withOwnedIndexId(newIndexId));
            }

            // There shouldn't be any really, but it can happen - for example when crashing in a constraint creation.
            // Letting these through.
            for (IndexToConnect indexToConnect : indexesToConnect) {
                schemaRuleMigrationAccess.writeSchemaRule(
                        indexToConnect.prototype.materialise(schemaRuleMigrationAccess.nextId()));
            }
        }
    }

    record IndexToConnect(long oldId, OptionalLong oldConstraintId, IndexPrototype prototype) {}

    record ConstraintToConnect(long oldId, long indexId, ConstraintDescriptor prototype) {}

    /**
     * Only to be used for expected types - no token indexes
     * Creates any tokens that are missing.
     */
    private static SchemaDescriptor translateToNewSchema(
            SchemaDescriptor schema, TokenRead tokenRead, TokenHolders dstTokenHolders) throws KernelException {
        int[] propertyIds = schema.getPropertyIds();
        int[] newPropertyIds = new int[propertyIds.length];
        for (int i = 0; i < propertyIds.length; i++) {
            newPropertyIds[i] =
                    dstTokenHolders.propertyKeyTokens().getOrCreateId(tokenRead.propertyKeyName(propertyIds[i]));
        }
        boolean forNodes = EntityType.NODE.equals(schema.entityType());

        // Fulltext is special and can have multiple entityTokens
        if (schema.isFulltextSchemaDescriptor()) {
            int[] entityTokenIds = schema.getEntityTokenIds();
            int[] newEntityTokenIds = new int[entityTokenIds.length];
            for (int i = 0; i < entityTokenIds.length; i++) {
                newEntityTokenIds[i] = forNodes
                        ? dstTokenHolders.labelTokens().getOrCreateId(tokenRead.nodeLabelName(entityTokenIds[i]))
                        : dstTokenHolders
                                .relationshipTypeTokens()
                                .getOrCreateId(tokenRead.relationshipTypeName(entityTokenIds[i]));
            }
            return SchemaDescriptors.fulltext(schema.entityType(), newEntityTokenIds, newPropertyIds);
        }

        if (forNodes) {
            return SchemaDescriptors.forLabel(
                    dstTokenHolders.labelTokens().getOrCreateId(tokenRead.nodeLabelName(schema.getLabelId())),
                    newPropertyIds);
        }
        return SchemaDescriptors.forRelType(
                dstTokenHolders
                        .relationshipTypeTokens()
                        .getOrCreateId(tokenRead.relationshipTypeName(schema.getRelTypeId())),
                newPropertyIds);
    }
}
