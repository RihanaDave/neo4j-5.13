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
package org.neo4j.metatest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.test.BatchTransaction.beginBatchTx;

import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.helpers.progress.ProgressListener;
import org.neo4j.test.BatchTransaction;

class BatchTransactionTest {
    @Test
    void shouldUseProgressListener() {
        // GIVEN
        Transaction transaction = mock(Transaction.class);
        GraphDatabaseService db = mock(GraphDatabaseService.class);
        when(db.beginTx()).thenReturn(transaction);
        ProgressListener progress = mock(ProgressListener.class);
        BatchTransaction tx = beginBatchTx(db).withIntermediarySize(10).withProgress(progress);

        // WHEN
        tx.increment();
        tx.increment(9);

        // THEN
        verify(db, times(2)).beginTx();
        verify(transaction).commit();
        verify(progress).add(1);
        verify(progress).add(9);
    }
}
