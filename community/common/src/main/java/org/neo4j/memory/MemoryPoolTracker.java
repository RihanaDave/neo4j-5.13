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
package org.neo4j.memory;

class MemoryPoolTracker implements MemoryTracker {
    private final ScopedMemoryPool pool;

    MemoryPoolTracker(ScopedMemoryPool pool) {
        this.pool = pool;
    }

    @Override
    public long usedNativeMemory() {
        return pool.usedNative();
    }

    @Override
    public long estimatedHeapMemory() {
        return pool.usedHeap();
    }

    @Override
    public void allocateNative(long bytes) {
        pool.reserveNative(bytes);
    }

    @Override
    public void releaseNative(long bytes) {
        pool.releaseNative(bytes);
    }

    @Override
    public void allocateHeap(long bytes) {
        pool.reserveHeap(bytes);
    }

    @Override
    public void releaseHeap(long bytes) {
        pool.releaseHeap(bytes);
    }

    @Override
    public long heapHighWaterMark() {
        return -1;
    }

    @Override
    public void reset() {}

    @Override
    public MemoryTracker getScopedMemoryTracker() {
        return new DefaultScopedMemoryTracker(this);
    }
}
