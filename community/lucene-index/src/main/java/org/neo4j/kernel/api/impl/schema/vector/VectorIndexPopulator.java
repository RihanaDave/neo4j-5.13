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
package org.neo4j.kernel.api.impl.schema.vector;

import org.apache.lucene.document.Document;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.api.impl.index.DatabaseIndex;
import org.neo4j.kernel.api.impl.schema.populator.LuceneIndexPopulator;
import org.neo4j.kernel.api.index.IndexUpdater;
import org.neo4j.kernel.impl.index.schema.IndexUpdateIgnoreStrategy;
import org.neo4j.storageengine.api.ValueIndexEntryUpdate;
import org.neo4j.values.storable.FloatingPointArray;

class VectorIndexPopulator extends LuceneIndexPopulator<DatabaseIndex<VectorIndexReader>> {
    private final VectorSimilarityFunction similarityFunction;

    VectorIndexPopulator(
            DatabaseIndex<VectorIndexReader> luceneIndex,
            IndexUpdateIgnoreStrategy ignoreStrategy,
            VectorSimilarityFunction similarityFunction) {
        super(luceneIndex, ignoreStrategy);
        this.similarityFunction = similarityFunction;
    }

    @Override
    public IndexUpdater newPopulatingUpdater(CursorContext cursorContext) {
        return new VectorIndexPopulatingUpdater(writer, ignoreStrategy, similarityFunction);
    }

    @Override
    protected Document updateAsDocument(ValueIndexEntryUpdate<?> update) {
        final var entityId = update.getEntityId();
        final var value = (FloatingPointArray) update.values()[0];
        return VectorDocumentStructure.createLuceneDocument(entityId, value, similarityFunction);
    }
}
