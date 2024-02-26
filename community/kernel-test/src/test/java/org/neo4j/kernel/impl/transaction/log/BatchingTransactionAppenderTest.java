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
package org.neo4j.kernel.impl.transaction.log;

import static java.util.Collections.emptyIterator;
import static org.apache.commons.io.IOUtils.EMPTY_BYTE_ARRAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.common.Subject.ANONYMOUS;
import static org.neo4j.io.pagecache.context.CursorContext.NULL_CONTEXT;
import static org.neo4j.kernel.impl.transaction.log.LogIndexEncoding.encodeLogIndex;
import static org.neo4j.kernel.impl.transaction.log.TestLogEntryReader.logEntryReader;
import static org.neo4j.kernel.impl.transaction.log.entry.LogEntryFactory.newCommitEntry;
import static org.neo4j.kernel.impl.transaction.log.entry.LogEntryFactory.newStartEntry;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;
import static org.neo4j.storageengine.api.Commitment.NO_COMMITMENT;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_CHECKSUM;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_COMMIT_TIMESTAMP;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_ID;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_CONSENSUS_INDEX;
import static org.neo4j.test.LatestVersions.LATEST_KERNEL_VERSION;
import static org.neo4j.test.LatestVersions.LATEST_KERNEL_VERSION_PROVIDER;

import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.neo4j.internal.kernel.api.exceptions.TransactionFailureException;
import org.neo4j.io.memory.HeapScopedBuffer;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.KernelVersionProvider;
import org.neo4j.kernel.impl.api.InternalTransactionCommitProcess;
import org.neo4j.kernel.impl.api.TestCommand;
import org.neo4j.kernel.impl.api.TransactionToApply;
import org.neo4j.kernel.impl.api.txid.IdStoreTransactionIdGenerator;
import org.neo4j.kernel.impl.api.txid.TransactionIdGenerator;
import org.neo4j.kernel.impl.transaction.CommittedCommandBatch;
import org.neo4j.kernel.impl.transaction.CommittedTransactionRepresentation;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryCommit;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryReader;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryStart;
import org.neo4j.kernel.impl.transaction.log.files.LogFile;
import org.neo4j.kernel.impl.transaction.log.files.LogFiles;
import org.neo4j.kernel.impl.transaction.log.files.TransactionLogFiles;
import org.neo4j.kernel.impl.transaction.tracing.LogAppendEvent;
import org.neo4j.kernel.impl.transaction.tracing.TransactionWriteEvent;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.monitoring.DatabaseHealth;
import org.neo4j.monitoring.Panic;
import org.neo4j.storageengine.api.CommandBatch;
import org.neo4j.storageengine.api.StorageCommand;
import org.neo4j.storageengine.api.StorageEngine;
import org.neo4j.storageengine.api.TransactionApplicationMode;
import org.neo4j.storageengine.api.TransactionId;
import org.neo4j.storageengine.api.TransactionIdStore;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.test.LatestVersions;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.LifeExtension;

@ExtendWith(LifeExtension.class)
class BatchingTransactionAppenderTest {
    @Inject
    private LifeSupport life;

    private final InMemoryVersionableReadableClosablePositionAwareChannel channel =
            new InMemoryVersionableReadableClosablePositionAwareChannel();
    private final LogAppendEvent logAppendEvent = LogAppendEvent.NULL;
    private final Panic databasePanic = mock(DatabaseHealth.class);
    private final LogFile logFile = mock(LogFile.class);
    private final LogFiles logFiles = mock(TransactionLogFiles.class);
    private final TransactionIdStore transactionIdStore = mock(TransactionIdStore.class);
    private final TransactionMetadataCache positionCache = new TransactionMetadataCache();
    private final TransactionIdGenerator transactionIdGenerator = new IdStoreTransactionIdGenerator(transactionIdStore);

    @BeforeEach
    void setUp() {
        when(logFiles.getLogFile()).thenReturn(logFile);
        when(transactionIdStore.getLastCommittedTransaction())
                .thenReturn(new TransactionId(BASE_TX_ID, BASE_TX_CHECKSUM, 1, 2));
    }

    @Test
    void shouldBeAbleToAppendTransactionWithoutKernelVersion()
            throws IOException, ExecutionException, InterruptedException {
        CountingVersionProvider versionProvider = new CountingVersionProvider();
        when(logFile.getTransactionLogWriter())
                .thenReturn(new TransactionLogWriter(channel, versionProvider, LatestVersions.BINARY_VERSIONS));

        long txId = 15;
        when(transactionIdStore.nextCommittingTransactionId()).thenReturn(txId);
        when(transactionIdStore.getLastCommittedTransaction())
                .thenReturn(
                        new TransactionId(txId, BASE_TX_CHECKSUM, BASE_TX_COMMIT_TIMESTAMP, UNKNOWN_CONSENSUS_INDEX));
        TransactionAppender appender = life.add(createTransactionAppender());
        CommandBatch transaction =
                new CompleteTransaction(singleTestCommand(), 5, 12345, 7896, 123456, -1, null, ANONYMOUS);

        assertEquals(0, versionProvider.getVersionLookedUp());

        appender.append(
                new TransactionToApply(
                        transaction, NULL_CONTEXT, StoreCursors.NULL, NO_COMMITMENT, TransactionIdGenerator.EMPTY),
                logAppendEvent);

        assertEquals(1, versionProvider.getVersionLookedUp());
        final LogEntryReader logEntryReader = logEntryReader();
        try (var reader = new CommittedCommandBatchCursor(channel, logEntryReader)) {
            reader.next();
            CommittedCommandBatch commandBatch = reader.get();
            assertEquals(
                    LatestVersions.LATEST_KERNEL_VERSION,
                    commandBatch.commandBatch().kernelVersion());
        }
    }

    @Test
    void shouldAppendSingleTransaction() throws Exception {
        // GIVEN
        when(logFile.getTransactionLogWriter())
                .thenReturn(new TransactionLogWriter(
                        channel, LATEST_KERNEL_VERSION_PROVIDER, LatestVersions.BINARY_VERSIONS));
        long txId = 15;
        when(transactionIdStore.nextCommittingTransactionId()).thenReturn(txId);
        when(transactionIdStore.getLastCommittedTransaction())
                .thenReturn(
                        new TransactionId(txId, BASE_TX_CHECKSUM, BASE_TX_COMMIT_TIMESTAMP, UNKNOWN_CONSENSUS_INDEX));
        TransactionAppender appender = life.add(createTransactionAppender());

        // WHEN
        CommandBatch transaction = transaction(singleTestCommand(), 5, 12345, 4545, 12345 + 10);

        appender.append(
                new TransactionToApply(
                        transaction, NULL_CONTEXT, StoreCursors.NULL, NO_COMMITMENT, TransactionIdGenerator.EMPTY),
                logAppendEvent);

        // THEN
        final LogEntryReader logEntryReader = logEntryReader();
        try (CommittedCommandBatchCursor reader = new CommittedCommandBatchCursor(channel, logEntryReader)) {
            reader.next();
            CommittedCommandBatch commandBatch = reader.get();
            CommandBatch tx = commandBatch.commandBatch();
            assertEquals(transaction.consensusIndex(), tx.consensusIndex());
            assertEquals(transaction.getTimeStarted(), tx.getTimeStarted());
            assertEquals(transaction.getTimeCommitted(), commandBatch.timeWritten());
            assertEquals(transaction.getLatestCommittedTxWhenStarted(), tx.getLatestCommittedTxWhenStarted());
        }
    }

    @Test
    void shouldAppendBatchOfTransactions() throws Exception {
        // GIVEN
        TransactionLogWriter logWriter =
                new TransactionLogWriter(channel, LATEST_KERNEL_VERSION_PROVIDER, LatestVersions.BINARY_VERSIONS);
        TransactionLogWriter logWriterSpy = spy(logWriter);
        when(logFile.getTransactionLogWriter()).thenReturn(logWriterSpy);

        TransactionAppender appender = life.add(createTransactionAppender());
        when(transactionIdStore.nextCommittingTransactionId()).thenReturn(2L, 3L, 4L);
        CommandBatch batch1 = transaction(singleTestCommand(), 0, 0, 1, 0);
        CommandBatch batch2 = transaction(singleTestCommand(), 0, 0, 1, 0);
        CommandBatch batch3 = transaction(singleTestCommand(), 0, 0, 1, 0);
        TransactionToApply batch = batchOf(batch1, batch2, batch3);

        // WHEN
        appender.append(batch, logAppendEvent);

        // THEN
        verify(logWriterSpy).append(eq(batch1), eq(2L), anyLong(), anyInt(), any(LogPosition.class));
        verify(logWriterSpy).append(eq(batch2), eq(3L), anyLong(), anyInt(), any(LogPosition.class));
        verify(logWriterSpy).append(eq(batch3), eq(4L), anyLong(), anyInt(), any(LogPosition.class));
    }

    @Test
    void shouldAppendCommittedTransactions() throws Exception {
        // GIVEN
        when(logFile.getTransactionLogWriter())
                .thenReturn(new TransactionLogWriter(
                        channel, LATEST_KERNEL_VERSION_PROVIDER, LatestVersions.BINARY_VERSIONS));

        long nextTxId = 15;
        when(transactionIdStore.nextCommittingTransactionId()).thenReturn(nextTxId);
        when(transactionIdStore.getLastCommittedTransaction())
                .thenReturn(new TransactionId(
                        nextTxId, BASE_TX_CHECKSUM, BASE_TX_COMMIT_TIMESTAMP, UNKNOWN_CONSENSUS_INDEX));
        TransactionAppender appender =
                life.add(new BatchingTransactionAppender(logFiles, transactionIdStore, databasePanic));

        // WHEN
        final byte[] additionalHeader = encodeLogIndex(5);
        final long timeStarted = 12345;
        long latestCommittedTxWhenStarted = nextTxId - 5;
        long timeCommitted = timeStarted + 10;
        LogEntryStart start = newStartEntry(
                LATEST_KERNEL_VERSION,
                timeStarted,
                latestCommittedTxWhenStarted,
                0,
                additionalHeader,
                LogPosition.UNSPECIFIED);
        LogEntryCommit commit = newCommitEntry(LATEST_KERNEL_VERSION, nextTxId, timeCommitted, BASE_TX_CHECKSUM);
        CommittedTransactionRepresentation transaction =
                new CommittedTransactionRepresentation(start, singleTestCommand(), commit);

        appender.append(
                new TransactionToApply(
                        transaction,
                        NULL_CONTEXT,
                        StoreCursors.NULL,
                        new TransactionCommitment(positionCache, transactionIdStore),
                        transactionIdGenerator),
                logAppendEvent);

        // THEN
        LogEntryReader logEntryReader = logEntryReader();
        try (CommittedCommandBatchCursor reader = new CommittedCommandBatchCursor(channel, logEntryReader)) {
            reader.next();
            CommittedCommandBatch commandBatch = reader.get();
            CommandBatch result = commandBatch.commandBatch();
            assertEquals(5, result.consensusIndex());
            assertEquals(timeStarted, result.getTimeStarted());
            assertEquals(timeCommitted, commandBatch.timeWritten());
            assertEquals(latestCommittedTxWhenStarted, result.getLatestCommittedTxWhenStarted());
        }
    }

    @Test
    void shouldNotAppendCommittedTransactionsWhenTooFarAhead() {
        // GIVEN
        InMemoryClosableChannel channel = new InMemoryClosableChannel();
        when(logFile.getTransactionLogWriter())
                .thenReturn(new TransactionLogWriter(
                        channel, LATEST_KERNEL_VERSION_PROVIDER, LatestVersions.BINARY_VERSIONS));

        TransactionAppender appender = life.add(createTransactionAppender());

        // WHEN
        final byte[] additionalHeader = encodeLogIndex(5);
        final long timeStarted = 12345;
        long latestCommittedTxWhenStarted = 4545;
        long timeCommitted = timeStarted + 10;
        when(transactionIdStore.getLastCommittedTransactionId()).thenReturn(latestCommittedTxWhenStarted);

        LogEntryStart start = newStartEntry(
                LATEST_KERNEL_VERSION, 0L, latestCommittedTxWhenStarted, 0, additionalHeader, LogPosition.UNSPECIFIED);
        LogEntryCommit commit = newCommitEntry(
                LATEST_KERNEL_VERSION, latestCommittedTxWhenStarted + 2, timeCommitted, BASE_TX_CHECKSUM);
        CommittedTransactionRepresentation transaction =
                new CommittedTransactionRepresentation(start, singleTestCommand(), commit);

        var e = assertThrows(
                Exception.class,
                () -> appender.append(
                        new TransactionToApply(
                                transaction,
                                NULL_CONTEXT,
                                StoreCursors.NULL,
                                new TransactionCommitment(positionCache, transactionIdStore),
                                new IdStoreTransactionIdGenerator(transactionIdStore)),
                        logAppendEvent));
        assertThat(e.getMessage()).contains("to be applied, but appending it ended up generating an");
    }

    @Test
    void shouldNotCallTransactionClosedOnFailedAppendedTransaction() throws Exception {
        // GIVEN
        long txId = 3;
        String failureMessage = "Forces a failure";
        FlushableLogPositionAwareChannel channel = spy(new PhysicalFlushableLogPositionAwareChannel(
                mock(PhysicalLogVersionedStoreChannel.class),
                new HeapScopedBuffer(Long.BYTES * 2, ByteOrder.LITTLE_ENDIAN, INSTANCE)));
        IOException failure = new IOException(failureMessage);
        when(channel.putLong(anyLong())).thenThrow(failure);
        when(logFile.getTransactionLogWriter())
                .thenReturn(new TransactionLogWriter(
                        channel, LATEST_KERNEL_VERSION_PROVIDER, LatestVersions.BINARY_VERSIONS));

        when(transactionIdStore.nextCommittingTransactionId()).thenReturn(txId);
        when(transactionIdStore.getLastCommittedTransaction())
                .thenReturn(
                        new TransactionId(txId, BASE_TX_CHECKSUM, BASE_TX_COMMIT_TIMESTAMP, UNKNOWN_CONSENSUS_INDEX));
        Mockito.reset(databasePanic);
        TransactionAppender appender = life.add(createTransactionAppender());

        // WHEN
        CommandBatch transaction = mock(CommandBatch.class);
        when(transaction.consensusIndex()).thenReturn(0L);
        when(transaction.isFirst()).thenReturn(true);
        when(transaction.isLast()).thenReturn(true);
        when(transaction.kernelVersion()).thenReturn(LatestVersions.LATEST_KERNEL_VERSION);

        var e = assertThrows(
                IOException.class,
                () -> appender.append(
                        new TransactionToApply(
                                transaction,
                                NULL_CONTEXT,
                                StoreCursors.NULL,
                                new TransactionCommitment(positionCache, transactionIdStore),
                                transactionIdGenerator),
                        logAppendEvent));
        assertSame(failure, e);
        verify(transactionIdStore).nextCommittingTransactionId();
        verify(transactionIdStore, never())
                .transactionClosed(eq(txId), anyLong(), anyLong(), anyInt(), anyLong(), anyLong());
        verify(databasePanic).panic(failure);
    }

    @Test
    void shouldNotCallTransactionClosedOnFailedForceLogToDisk() throws Exception {
        // GIVEN
        long txId = 3;
        String failureMessage = "Forces a failure";
        FlushableLogPositionAwareChannel channel = spy(new InMemoryClosableChannel());
        IOException failure = new IOException(failureMessage);
        final Flushable flushable = mock(Flushable.class);
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    return flushable;
                })
                .when(channel)
                .prepareForFlush();
        when(logFile.forceAfterAppend(any())).thenThrow(failure);
        when(logFile.getTransactionLogWriter())
                .thenReturn(new TransactionLogWriter(
                        channel, LATEST_KERNEL_VERSION_PROVIDER, LatestVersions.BINARY_VERSIONS));

        TransactionMetadataCache metadataCache = new TransactionMetadataCache();
        TransactionIdStore transactionIdStore = mock(TransactionIdStore.class);
        when(transactionIdStore.nextCommittingTransactionId()).thenReturn(txId);
        when(transactionIdStore.getLastCommittedTransaction())
                .thenReturn(
                        new TransactionId(txId, BASE_TX_CHECKSUM, BASE_TX_COMMIT_TIMESTAMP, UNKNOWN_CONSENSUS_INDEX));
        TransactionAppender appender =
                life.add(new BatchingTransactionAppender(logFiles, transactionIdStore, databasePanic));

        // WHEN
        CommandBatch commandBatch = mock(CommandBatch.class);
        when(commandBatch.consensusIndex()).thenReturn(0L);
        when(commandBatch.kernelVersion()).thenReturn(LatestVersions.LATEST_KERNEL_VERSION);
        when(commandBatch.iterator()).thenReturn(emptyIterator());
        when(commandBatch.isFirst()).thenReturn(true);
        when(commandBatch.isLast()).thenReturn(true);

        var e = assertThrows(
                IOException.class,
                () -> appender.append(
                        new TransactionToApply(
                                commandBatch,
                                NULL_CONTEXT,
                                StoreCursors.NULL,
                                new TransactionCommitment(metadataCache, transactionIdStore),
                                new IdStoreTransactionIdGenerator(transactionIdStore)),
                        logAppendEvent));
        assertSame(failure, e);
        verify(transactionIdStore).nextCommittingTransactionId();
        verify(transactionIdStore, never())
                .transactionClosed(eq(txId), anyLong(), anyLong(), anyInt(), anyLong(), anyLong());
    }

    @Test
    void shouldFailIfTransactionIdsMismatch() {
        // Given
        BatchingTransactionAppender appender = life.add(createTransactionAppender());
        var commitProcess =
                new InternalTransactionCommitProcess(appender, mock(StorageEngine.class, RETURNS_MOCKS), false);
        when(transactionIdStore.nextCommittingTransactionId()).thenReturn(42L);
        var transactionCommitment = new TransactionCommitment(positionCache, transactionIdStore);
        var transactionIdGenerator = new IdStoreTransactionIdGenerator(transactionIdStore);
        var transaction = new CommittedTransactionRepresentation(
                newStartEntry(LATEST_KERNEL_VERSION, 1, 2, 3, EMPTY_BYTE_ARRAY, LogPosition.UNSPECIFIED),
                singleTestCommand(),
                newCommitEntry(LATEST_KERNEL_VERSION, 11, 1L, BASE_TX_CHECKSUM));
        TransactionToApply batch = new TransactionToApply(
                transaction, NULL_CONTEXT, StoreCursors.NULL, transactionCommitment, transactionIdGenerator);
        assertThrows(
                TransactionFailureException.class,
                () -> commitProcess.commit(batch, TransactionWriteEvent.NULL, TransactionApplicationMode.EXTERNAL));
    }

    private BatchingTransactionAppender createTransactionAppender() {
        return new BatchingTransactionAppender(logFiles, transactionIdStore, databasePanic);
    }

    private static CommandBatch transaction(
            List<StorageCommand> commands,
            long consensusIndex,
            long timeStarted,
            long latestCommittedTxWhenStarted,
            long timeCommitted) {
        return new CompleteTransaction(
                commands,
                consensusIndex,
                timeStarted,
                latestCommittedTxWhenStarted,
                timeCommitted,
                -1,
                LatestVersions.LATEST_KERNEL_VERSION,
                ANONYMOUS);
    }

    private static List<StorageCommand> singleTestCommand() {
        return Collections.singletonList(new TestCommand());
    }

    private TransactionToApply batchOf(CommandBatch... transactions) {
        TransactionToApply first = null;
        TransactionToApply last = null;
        var transactionCommitment = new TransactionCommitment(positionCache, transactionIdStore);
        for (CommandBatch transaction : transactions) {
            TransactionToApply tx = new TransactionToApply(
                    transaction, NULL_CONTEXT, StoreCursors.NULL, transactionCommitment, transactionIdGenerator);
            if (first == null) {
                first = last = tx;
            } else {
                last.next(tx);
                last = tx;
            }
        }
        return first;
    }

    private static class CountingVersionProvider implements KernelVersionProvider {
        private int versionLookedUp;

        @Override
        public KernelVersion kernelVersion() {
            versionLookedUp++;
            return LatestVersions.LATEST_KERNEL_VERSION;
        }

        public int getVersionLookedUp() {
            return versionLookedUp;
        }
    }
}
