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
package org.neo4j.index.internal.gbptree;

class SeekCursorDynamicSizeTest extends SeekCursorTestBase<RawBytes, RawBytes> {
    @Override
    TestLayout<RawBytes, RawBytes> getLayout() {
        return new SimpleByteArrayLayout();
    }

    @Override
    protected LeafNodeBehaviour<RawBytes, RawBytes> getLeaf(
            int pageSize, Layout<RawBytes, RawBytes> layout, OffloadStore<RawBytes, RawBytes> offloadStore) {
        return new LeafNodeDynamicSize<>(pageSize, layout, offloadStore);
    }

    @Override
    protected InternalNodeBehaviour<RawBytes> getInternal(
            int pageSize, Layout<RawBytes, RawBytes> layout, OffloadStore<RawBytes, RawBytes> offloadStore) {
        return new InternalNodeDynamicSize<>(pageSize, layout, offloadStore);
    }
}
