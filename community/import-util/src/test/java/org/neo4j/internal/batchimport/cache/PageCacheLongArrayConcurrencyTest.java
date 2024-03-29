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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.io.pagecache.context.FixedVersionContextSupplier.EMPTY_CONTEXT_SUPPLIER;

import java.io.IOException;
import org.neo4j.io.pagecache.PagedFile;
import org.neo4j.io.pagecache.context.CursorContextFactory;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;

public class PageCacheLongArrayConcurrencyTest extends PageCacheNumberArrayConcurrencyTest {
    @Override
    protected Runnable wholeFileRacer(NumberArray array, int contestant) {
        return new WholeFileRacer((PageCacheLongArray) array);
    }

    @Override
    protected Runnable fileRangeRacer(NumberArray array, int contestant) {
        return new FileRangeRacer((PageCacheLongArray) array, contestant);
    }

    @Override
    protected PageCacheLongArray getNumberArray(PagedFile file) throws IOException {
        return new PageCacheLongArray(
                file, new CursorContextFactory(PageCacheTracer.NULL, EMPTY_CONTEXT_SUPPLIER), COUNT, 0, 0);
    }

    private static class WholeFileRacer implements Runnable {
        private final LongArray array;

        WholeFileRacer(LongArray array) {
            this.array = array;
        }

        @Override
        public void run() {

            for (int o = 0; o < LAPS; o++) {
                for (long i = 0; i < COUNT; i++) {
                    array.set(i, i);
                    assertEquals(i, array.get(i));
                }
            }
        }
    }

    private class FileRangeRacer implements Runnable {
        private final LongArray array;
        private final int contestant;

        FileRangeRacer(LongArray array, int contestant) {
            this.array = array;
            this.contestant = contestant;
        }

        @Override
        public void run() {

            for (int o = 0; o < LAPS; o++) {
                for (long i = contestant; i < COUNT; i += CONTESTANTS) {
                    long value = random.nextLong();
                    array.set(i, value);
                    assertEquals(value, array.get(i));
                }
            }
        }
    }
}
