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
package org.neo4j.kernel.impl.api;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import org.neo4j.time.SystemNanoClock;

public class TransactionExecutionStatistic {
    public static final TransactionExecutionStatistic NOT_AVAILABLE = new TransactionExecutionStatistic();

    private final Long heapAllocatedBytes;
    private final Long nativeAllocatedBytes;
    private final Long estimatedUsedHeapMemory;
    private final Long cpuTimeMillis;
    private final long waitTimeMillis;
    private final long elapsedTimeMillis;
    private final Long idleTimeMillis;
    private final long pageFaults;
    private final long pageHits;

    private TransactionExecutionStatistic() {
        heapAllocatedBytes = null;
        nativeAllocatedBytes = null;
        estimatedUsedHeapMemory = null;
        cpuTimeMillis = null;
        waitTimeMillis = -1;
        elapsedTimeMillis = -1;
        idleTimeMillis = null;
        pageFaults = 0;
        pageHits = 0;
    }

    public TransactionExecutionStatistic(
            KernelTransactionImplementation tx, SystemNanoClock clock, long startTimeMillis) {
        long nowMillis = clock.millis();
        long nowNanos = clock.nanos();
        KernelTransactionImplementation.Statistics statistics = tx.getStatistics();
        this.waitTimeMillis = NANOSECONDS.toMillis(statistics.getWaitingTimeNanos(nowNanos));
        this.heapAllocatedBytes = nullIfNegative(statistics.heapAllocatedBytes());
        this.nativeAllocatedBytes = nullIfNegative(statistics.usedNativeMemory());
        this.estimatedUsedHeapMemory = nullIfNegative(statistics.estimatedHeapMemory());
        this.cpuTimeMillis = nullIfNegative(statistics.cpuTimeMillis());
        var tracer = tx.concurrentCursorContextLookup().getCursorTracer();
        this.pageFaults = tracer.faults();
        this.pageHits = tracer.hits();
        this.elapsedTimeMillis = nowMillis - startTimeMillis;
        this.idleTimeMillis =
                this.cpuTimeMillis != null ? elapsedTimeMillis - this.cpuTimeMillis - waitTimeMillis : null;
    }

    public Long getEstimatedUsedHeapMemory() {
        return estimatedUsedHeapMemory;
    }

    public Long getHeapAllocatedBytes() {
        return heapAllocatedBytes;
    }

    public Long getNativeAllocatedBytes() {
        return nativeAllocatedBytes;
    }

    public Long getCpuTimeMillis() {
        return cpuTimeMillis;
    }

    public long getWaitTimeMillis() {
        return waitTimeMillis;
    }

    public long getElapsedTimeMillis() {
        return elapsedTimeMillis;
    }

    public Long getIdleTimeMillis() {
        return idleTimeMillis;
    }

    public long getPageHits() {
        return pageHits;
    }

    public long getPageFaults() {
        return pageFaults;
    }

    private static Long nullIfNegative(long value) {
        return value >= 0 ? value : null;
    }
}
