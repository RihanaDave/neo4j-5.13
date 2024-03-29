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
package org.neo4j.internal.batchimport.cache;

import static java.lang.Long.min;
import static org.neo4j.internal.helpers.ArrayUtil.MAX_ARRAY_SIZE;

import org.neo4j.memory.MemoryTracker;

/**
 * Used as part of the fallback strategy for {@link NumberArrayFactories.Auto}. Tries to split up fixed-size arrays
 * ({@link NumberArrayFactory#newLongArray(long, long, MemoryTracker)} and
 * {@link NumberArrayFactory#newIntArray(long, int, MemoryTracker)} into smaller chunks where
 * some can live on heap and some off heap.
 */
public class ChunkedNumberArrayFactory extends NumberArrayFactory.Adapter {
    static final int MAGIC_CHUNK_COUNT = 10;
    private final NumberArrayFactory delegate;

    ChunkedNumberArrayFactory(Monitor monitor, NumberArrayFactory... delegateList) {
        delegate = new NumberArrayFactories.Auto(monitor, delegateList);
    }

    @Override
    public LongArray newLongArray(long length, long defaultValue, long base, MemoryTracker memoryTracker) {
        // Here we want to have the property of a dynamic array so that some parts of the array
        // can live on heap, some off.
        return newDynamicLongArray(fractionOf(length), defaultValue, memoryTracker);
    }

    @Override
    public IntArray newIntArray(long length, int defaultValue, long base, MemoryTracker memoryTracker) {
        // Here we want to have the property of a dynamic array so that some parts of the array
        // can live on heap, some off.
        return newDynamicIntArray(fractionOf(length), defaultValue, memoryTracker);
    }

    @Override
    public ByteArray newByteArray(long length, byte[] defaultValue, long base, MemoryTracker memoryTracker) {
        // Here we want to have the property of a dynamic array so that some parts of the array
        // can live on heap, some off.
        return newDynamicByteArray(fractionOf(length), defaultValue, memoryTracker);
    }

    private static long fractionOf(long length) {
        if (length < MAGIC_CHUNK_COUNT) {
            return length;
        }
        return min(length / MAGIC_CHUNK_COUNT, MAX_ARRAY_SIZE);
    }

    @Override
    public IntArray newDynamicIntArray(long chunkSize, int defaultValue, MemoryTracker memoryTracker) {
        return new DynamicIntArray(delegate, chunkSize, defaultValue, memoryTracker);
    }

    @Override
    public LongArray newDynamicLongArray(long chunkSize, long defaultValue, MemoryTracker memoryTracker) {
        return new DynamicLongArray(delegate, chunkSize, defaultValue, memoryTracker);
    }

    @Override
    public ByteArray newDynamicByteArray(long chunkSize, byte[] defaultValue, MemoryTracker memoryTracker) {
        return new DynamicByteArray(delegate, chunkSize, defaultValue, memoryTracker);
    }

    @Override
    public String toString() {
        return "ChunkedNumberArrayFactory with delegate " + delegate;
    }
}
