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
package org.neo4j.kernel.impl.transaction.log.pruning;

import java.nio.file.Path;
import org.neo4j.kernel.impl.transaction.log.LogFileInformation;

public final class FileCountThreshold implements Threshold {
    private final long maxNonEmptyLogs;

    private long nonEmptyLogCount;

    FileCountThreshold(long maxNonEmptyLogs) {
        this.maxNonEmptyLogs = maxNonEmptyLogs;
    }

    @Override
    public void init() {
        nonEmptyLogCount = 0;
    }

    @Override
    public boolean reached(Path file, long version, LogFileInformation source) {
        return ++nonEmptyLogCount >= maxNonEmptyLogs;
    }

    @Override
    public String toString() {
        return maxNonEmptyLogs + " files";
    }
}
