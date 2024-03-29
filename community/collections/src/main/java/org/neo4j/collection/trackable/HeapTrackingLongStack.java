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
package org.neo4j.collection.trackable;

import java.util.EmptyStackException;
import org.neo4j.graphdb.Resource;

public class HeapTrackingLongStack implements Resource {
    private final HeapTrackingLongArrayList delegate;

    public HeapTrackingLongStack(HeapTrackingLongArrayList delegate) {
        this.delegate = delegate;
    }

    public long peek() {
        int size = delegate.size();
        if (size == 0) {
            throw new EmptyStackException();
        }
        return delegate.get(size - 1);
    }

    public void push(long item) {
        delegate.add(item);
    }

    public long pop() {
        return delegate.removeLast();
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean notEmpty() {
        return size() != 0;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
