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
package org.neo4j.kernel.internal;

import java.nio.file.Path;
import java.util.function.Predicate;
import org.neo4j.kernel.api.impl.schema.TextIndexProvider;
import org.neo4j.kernel.api.impl.schema.trigram.TrigramIndexProvider;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexProvider;
import org.neo4j.kernel.api.index.IndexDirectoryStructure;
import org.neo4j.kernel.impl.index.schema.FulltextIndexProviderFactory;

/**
 * A filter which only matches native index files.
 * This class contains logic that is really index provider specific, but to ask index providers becomes tricky since
 * they aren't always available and this filter is also expected to be used in offline scenarios.
 *
 * The basic idea is to include everything except known lucene files (or directories known to include lucene files).
 */
public class NativeIndexFileFilter implements Predicate<Path> {
    private final Path indexRoot;

    public NativeIndexFileFilter(Path storeDir) {
        indexRoot = IndexDirectoryStructure.baseSchemaIndexFolder(storeDir).toAbsolutePath();
    }

    @Override
    public boolean test(Path path) {
        if (!path.toAbsolutePath().startsWith(indexRoot)) {
            // This file isn't even under the schema/index root directory
            return false;
        }

        Path schemaPath = indexRoot.relativize(path);
        int nameCount = schemaPath.getNameCount();

        if (nameCount == 0) {
            return false;
        }

        // - schema/index/text-1.0
        // - schema/index/text-2.0
        // - schema/index/fulltext-1.0
        // - schema/index/vector-1.0
        String schemaBaseName = schemaPath.getName(0).toString();
        boolean isLuceneBackedTextIndex = schemaBaseName.equals(TextIndexProvider.DESCRIPTOR.name())
                || schemaBaseName.equals(TrigramIndexProvider.DESCRIPTOR.name())
                || schemaBaseName.equals(VectorIndexProvider.DESCRIPTOR.name())
                || schemaBaseName.equals(FulltextIndexProviderFactory.DESCRIPTOR.name());

        return !isLuceneBackedTextIndex;
    }
}
