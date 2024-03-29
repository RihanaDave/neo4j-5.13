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
package org.neo4j.io.pagecache.stress;

import static java.lang.System.nanoTime;

import java.util.concurrent.TimeUnit;
import org.neo4j.io.pagecache.monitoring.PageCacheCounters;

public final class Conditions {
    private Conditions() {}

    public static Condition numberOfEvictions(final PageCacheCounters monitor, final long desiredNumberOfEvictions) {
        return () -> monitor.evictions() > desiredNumberOfEvictions;
    }

    public static Condition timePeriod(final int duration, final TimeUnit timeUnit) {
        final long endTimeInNanos = nanoTime() + timeUnit.toNanos(duration);

        return () -> nanoTime() > endTimeInNanos;
    }
}
