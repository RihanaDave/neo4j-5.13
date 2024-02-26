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
package org.neo4j.kernel.impl.transaction.log.entry.v50;

import static org.neo4j.kernel.impl.transaction.log.entry.LogEntryTypeCodes.DETACHED_CHECK_POINT_V5_0;

import java.util.Objects;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.entry.AbstractVersionAwareLogEntry;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.storageengine.api.TransactionId;

public class LogEntryDetachedCheckpointV5_0 extends AbstractVersionAwareLogEntry {
    protected final TransactionId transactionId;
    protected final LogPosition logPosition;
    protected final long checkpointTime;
    protected final StoreId storeId;
    protected final String reason;

    public LogEntryDetachedCheckpointV5_0(
            KernelVersion kernelVersion,
            TransactionId transactionId,
            LogPosition logPosition,
            long checkpointMillis,
            StoreId storeId,
            String reason) {
        super(kernelVersion, DETACHED_CHECK_POINT_V5_0);
        this.transactionId = transactionId;
        this.logPosition = logPosition;
        this.checkpointTime = checkpointMillis;
        this.storeId = storeId;
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LogEntryDetachedCheckpointV5_0 that = (LogEntryDetachedCheckpointV5_0) o;
        return checkpointTime == that.checkpointTime
                && Objects.equals(transactionId, that.transactionId)
                && Objects.equals(logPosition, that.logPosition)
                && Objects.equals(storeId, that.storeId)
                && kernelVersion() == that.kernelVersion()
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kernelVersion(), transactionId, logPosition, checkpointTime, storeId, reason);
    }

    public StoreId getStoreId() {
        return storeId;
    }

    public LogPosition getLogPosition() {
        return logPosition;
    }

    public TransactionId getTransactionId() {
        return transactionId;
    }

    public String getReason() {
        return reason;
    }

    public long getCheckpointTime() {
        return checkpointTime;
    }

    @Override
    public String toString() {
        return "LogEntryDetachedCheckpointV5_0{" + "transactionId="
                + transactionId + ", logPosition="
                + logPosition + ", checkpointTime="
                + checkpointTime + ", storeId="
                + storeId + ", reason='"
                + reason + '\'' + ", version="
                + kernelVersion() + '}';
    }
}
