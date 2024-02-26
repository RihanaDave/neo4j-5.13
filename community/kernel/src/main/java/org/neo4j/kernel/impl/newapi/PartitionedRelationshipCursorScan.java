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
package org.neo4j.kernel.impl.newapi;

import org.eclipse.collections.impl.iterator.ImmutableEmptyLongIterator;
import org.neo4j.internal.kernel.api.RelationshipScanCursor;
import org.neo4j.internal.kernel.api.security.AccessMode;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.api.ExecutionContext;
import org.neo4j.storageengine.api.AllRelationshipsScan;

final class PartitionedRelationshipCursorScan
        extends PartitionedEntityCursorScan<RelationshipScanCursor, AllRelationshipsScan> {

    PartitionedRelationshipCursorScan(
            AllRelationshipsScan storageScan, Read read, int desiredNumberOfPartitions, long totalCount) {
        super(storageScan, read, desiredNumberOfPartitions, totalCount);
    }

    @Override
    public boolean reservePartition(RelationshipScanCursor cursor, CursorContext cursorContext, AccessMode accessMode) {
        return reservePartition(cursor, fallbackRead, accessMode);
    }

    @Override
    public boolean reservePartition(RelationshipScanCursor cursor, ExecutionContext executionContext) {
        return reservePartition(
                cursor,
                (org.neo4j.kernel.impl.newapi.Read) executionContext.dataRead(),
                executionContext.securityContext().mode());
    }

    private boolean reservePartition(RelationshipScanCursor cursor, Read read, AccessMode accessMode) {
        return ((DefaultRelationshipScanCursor) cursor)
                .scanBatch(
                        read, storageScan, computeBatchSize(), ImmutableEmptyLongIterator.INSTANCE, false, accessMode);
    }
}
