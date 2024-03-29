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
package org.neo4j.kernel.api.impl.index.collector;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.function.LongPredicate;

/**
 * Iterator over entity ids together with their respective score.
 */
public class ScoredEntityIterator implements ValuesIterator {
    private final ValuesIterator iterator;
    private final LongPredicate predicate;
    private boolean hasNext;
    private long currentEntityId;
    private float currentScore;
    private long nextEntityId;
    private float nextScore;

    private ScoredEntityIterator(ValuesIterator sortedValuesIterator, LongPredicate predicate) {
        this.iterator = sortedValuesIterator;
        this.predicate = predicate;
        advanceIterator();
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public long current() {
        return currentEntityId;
    }

    @Override
    public int remaining() {
        return iterator.remaining() + (hasNext ? 1 : 0);
    }

    @Override
    public float currentScore() {
        return currentScore;
    }

    @Override
    public long next() {
        if (hasNext) {
            currentEntityId = nextEntityId;
            currentScore = nextScore;
            advanceIterator();
            return currentEntityId;
        } else {
            throw new NoSuchElementException("The iterator is exhausted.");
        }
    }

    private void advanceIterator() {
        do {
            hasNext = iterator.hasNext();
            if (hasNext) {
                nextEntityId = iterator.next();
                nextScore = iterator.currentScore();
            }
        } while (hasNext && !predicate.test(nextEntityId));
    }

    public static ValuesIterator filter(ValuesIterator iterator, LongPredicate predicate) {
        return new ScoredEntityIterator(iterator, predicate);
    }

    /**
     * Merges the given iterators into a single iterator, that maintains the aggregate descending score sort order.
     *
     * @param iterators to concatenate
     * @return a {@link ScoredEntityIterator} that iterates over all of the elements in all of the given iterators
     */
    public static ValuesIterator mergeIterators(List<ValuesIterator> iterators) {
        if (iterators.size() == 1) {
            return iterators.get(0);
        }
        return new ConcatenatingScoredEntityIterator(iterators);
    }

    private static class ConcatenatingScoredEntityIterator implements ValuesIterator {
        private final PriorityQueue<ValuesIterator> sources;
        private boolean hasNext;
        private long entityId;
        private float score;

        ConcatenatingScoredEntityIterator(Iterable<? extends ValuesIterator> iterators) {
            // We take the delegate iterators in current score order, using the ordering defined in CIP2016-06-14, where
            // NaN comes between positive infinity
            // and the largest float/double value. This is the same as Float/Double.compare.
            sources = new PriorityQueue<>((o1, o2) -> Float.compare(o2.currentScore(), o1.currentScore()));
            for (final var iterator : iterators) {
                if (iterator.hasNext()) {
                    iterator.next();
                    sources.add(iterator);
                    hasNext = true;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public int remaining() {
            return 0;
        }

        @Override
        public float currentScore() {
            return score;
        }

        @Override
        public long next() {
            if (hasNext) {
                final var iterator = sources.poll();
                assert iterator != null;
                entityId = iterator.current();
                score = iterator.currentScore();
                if (iterator.hasNext()) {
                    iterator.next();
                    sources.add(iterator);
                }
                hasNext = !sources.isEmpty();
                return entityId;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public long current() {
            return entityId;
        }
    }
}
