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
package org.neo4j.values.utils;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.OffsetTime;
import java.time.ZoneOffset;

public final class TemporalUtil {
    public static final long NANOS_PER_SECOND = 1_000_000_000L;
    public static final long AVG_NANOS_PER_MONTH = 2_629_746_000_000_000L;

    public static final long SECONDS_PER_DAY = DAYS.getDuration().getSeconds();
    public static final long AVG_SECONDS_PER_MONTH = 2_629_746;

    private TemporalUtil() {}

    static OffsetTime truncateOffsetToMinutes(OffsetTime value) {
        int offsetMinutes = value.getOffset().getTotalSeconds() / 60;
        ZoneOffset truncatedOffset = ZoneOffset.ofTotalSeconds(offsetMinutes * 60);
        return value.withOffsetSameInstant(truncatedOffset);
    }

    public static long nanosOfDayToUTC(long nanosOfDayLocal, int offsetSeconds) {
        return nanosOfDayLocal - offsetSeconds * NANOS_PER_SECOND;
    }

    public static long nanosOfDayToLocal(long nanosOfDayUTC, int offsetSeconds) {
        return nanosOfDayUTC + (long) offsetSeconds * NANOS_PER_SECOND;
    }

    public static long getNanosOfDayUTC(OffsetTime value) {
        long secondsOfDayLocal = value.toLocalTime().toSecondOfDay();
        long secondsOffset = value.getOffset().getTotalSeconds();
        return (secondsOfDayLocal - secondsOffset) * NANOS_PER_SECOND + value.getNano();
    }
}
