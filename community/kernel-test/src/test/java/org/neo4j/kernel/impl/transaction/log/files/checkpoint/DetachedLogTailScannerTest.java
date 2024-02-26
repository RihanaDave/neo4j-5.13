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
package org.neo4j.kernel.impl.transaction.log.files.checkpoint;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.configuration.GraphDatabaseInternalSettings.fail_on_corrupted_log_files;
import static org.neo4j.kernel.impl.transaction.log.files.checkpoint.DetachedLogTailScanner.NO_TRANSACTION_ID;
import static org.neo4j.logging.AssertableLogProvider.Level.INFO;
import static org.neo4j.logging.LogAssertions.assertThat;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_CHECKSUM;
import static org.neo4j.storageengine.api.TransactionIdStore.UNKNOWN_TRANSACTION_ID;
import static org.neo4j.test.LatestVersions.LATEST_KERNEL_VERSION;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.configuration.Config;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.impl.api.TestCommandReaderFactory;
import org.neo4j.kernel.impl.transaction.SimpleLogVersionRepository;
import org.neo4j.kernel.impl.transaction.SimpleTransactionIdStore;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.LogTailMetadata;
import org.neo4j.kernel.impl.transaction.log.TransactionLogWriter;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryWriter;
import org.neo4j.kernel.impl.transaction.log.files.LogFile;
import org.neo4j.kernel.impl.transaction.log.files.LogFiles;
import org.neo4j.kernel.impl.transaction.log.files.LogFilesBuilder;
import org.neo4j.kernel.impl.transaction.log.files.LogTailInformation;
import org.neo4j.kernel.impl.transaction.tracing.LogCheckPointEvent;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.storageengine.api.LogVersionRepository;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.storageengine.api.TransactionId;
import org.neo4j.storageengine.api.TransactionIdStore;
import org.neo4j.test.LatestVersions;
import org.neo4j.test.extension.EphemeralNeo4jLayoutExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.pagecache.EphemeralPageCacheExtension;

@EphemeralPageCacheExtension
@EphemeralNeo4jLayoutExtension
class DetachedLogTailScannerTest {
    @Inject
    protected FileSystemAbstraction fs;

    @Inject
    protected PageCache pageCache;

    @Inject
    protected DatabaseLayout databaseLayout;

    protected LogFiles logFiles;
    protected AssertableLogProvider logProvider;
    protected LogVersionRepository logVersionRepository;
    protected TransactionIdStore transactionIdStore;

    private static Stream<Arguments> params() {
        return Stream.of(arguments(1, 2), arguments(42, 43));
    }

    @BeforeEach
    void setUp() throws IOException {
        logVersionRepository = new SimpleLogVersionRepository();
        transactionIdStore = new SimpleTransactionIdStore();
        logProvider = new AssertableLogProvider();
        logFiles = createLogFiles();
    }

    LogFiles createLogFiles() throws IOException {
        var storeId = new StoreId(1, 2, "engine-1", "format-1", 3, 4);
        return LogFilesBuilder.activeFilesBuilder(
                        databaseLayout, fs, pageCache, LatestVersions.LATEST_KERNEL_VERSION_PROVIDER)
                .withLogVersionRepository(logVersionRepository)
                .withTransactionIdStore(transactionIdStore)
                .withCommandReaderFactory(TestCommandReaderFactory.INSTANCE)
                .withStoreId(storeId)
                .withLogProvider(logProvider)
                .withConfig(Config.defaults(fail_on_corrupted_log_files, false))
                .build();
    }

    void writeCheckpoint(CheckpointFile separateCheckpointFile, TransactionId transactionId, LogPosition logPosition)
            throws IOException {
        separateCheckpointFile
                .getCheckpointAppender()
                .checkPoint(
                        LogCheckPointEvent.NULL,
                        transactionId,
                        LATEST_KERNEL_VERSION,
                        logPosition,
                        Instant.now(),
                        "test");
    }

    @Test
    void includeWrongPositionInException() throws Exception {
        var storeId = new StoreId(1, 2, "engine-1", "format-1", 3, 4);
        var testTogFiles = LogFilesBuilder.activeFilesBuilder(
                        databaseLayout, fs, pageCache, LatestVersions.LATEST_KERNEL_VERSION_PROVIDER)
                .withLogVersionRepository(logVersionRepository)
                .withTransactionIdStore(transactionIdStore)
                .withCommandReaderFactory(TestCommandReaderFactory.INSTANCE)
                .withStoreId(storeId)
                .withLogProvider(logProvider)
                .withConfig(Config.defaults(fail_on_corrupted_log_files, true))
                .build();

        long txId = 6;
        PositionEntry position = position();
        setupLogFiles(
                10,
                logFile(start(), commit(txId - 1), position),
                logFile(checkPoint(position)),
                logFile(start(), commit(txId)));

        // remove all tx log files
        Path[] matchedFiles = testTogFiles.getLogFile().getMatchedFiles();
        for (Path matchedFile : matchedFiles) {
            fs.delete(matchedFile);
        }

        var e = assertThrows(RuntimeException.class, testTogFiles::getTailMetadata);
        assertThat(e)
                .rootCause()
                .hasMessageContaining("LogPosition{logVersion=8,")
                .hasMessageContaining("checkpoint does not point to a valid location in transaction logs.");
    }

    @Test
    void detectMissingLogFiles() {
        LogTailMetadata tailInformation = logFiles.getTailMetadata();
        assertTrue(tailInformation.logsMissing());
        assertTrue(tailInformation.isRecoveryRequired());
    }

    @ParameterizedTest
    @MethodSource("params")
    void noLogFilesFound(int startLogVersion, int endLogVersion) throws Exception {
        // given no files
        setupLogFiles(endLogVersion);

        // when
        LogTailMetadata logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(false, false, NO_TRANSACTION_ID, true, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void oneLogFileNoCheckPoints(int startLogVersion, int endLogVersion) throws Exception {
        // given
        setupLogFiles(endLogVersion, logFile());

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(false, false, NO_TRANSACTION_ID, false, logTailInformation);
        assertFalse(logTailInformation.logsMissing());
    }

    @ParameterizedTest
    @MethodSource("params")
    void oneLogFileNoCheckPointsOneStart(int startLogVersion, int endLogVersion) throws Exception {
        // given
        long txId = 10;
        setupLogFiles(endLogVersion, logFile(start(), commit(txId)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(false, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void twoLogFilesNoCheckPoints(int startLogVersion, int endLogVersion) throws Exception {
        // given
        setupLogFiles(endLogVersion, logFile(), logFile());

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(false, false, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void twoLogFilesNoCheckPointsOneStart(int startLogVersion, int endLogVersion) throws Exception {
        // given
        long txId = 21;
        setupLogFiles(endLogVersion, logFile(), logFile(start(), commit(txId)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(false, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void twoLogFilesNoCheckPointsOneStartWithoutCommit(int startLogVersion, int endLogVersion) throws Exception {
        // given
        setupLogFiles(endLogVersion, logFile(), logFile(start()));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(false, true, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void twoLogFilesNoCheckPointsTwoCommits(int startLogVersion, int endLogVersion) throws Exception {
        // given
        long txId = 21;
        setupLogFiles(endLogVersion, logFile(), logFile(start(), commit(txId), start(), commit(txId + 1)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(false, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void twoLogFilesCheckPointTargetsPrevious(int startLogVersion, int endLogVersion) throws Exception {
        // given
        long txId = 6;
        PositionEntry position = position();
        setupLogFiles(
                endLogVersion,
                logFile(start(), commit(txId - 1), position),
                logFile(start(), commit(txId)),
                logFile(checkPoint(position)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void twoLogFilesStartAndCommitInDifferentFiles(int startLogVersion, int endLogVersion) throws Exception {
        // given
        long txId = 6;
        setupLogFiles(endLogVersion, logFile(start()), logFile(commit(txId)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(false, true, 6, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void latestLogFileContainingACheckPointOnly(int startLogVersion, int endLogVersion) throws Exception {
        // given
        setupLogFiles(endLogVersion, logFile(checkPoint()));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, false, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void latestLogFileContainingACheckPointAndAStartBefore(int startLogVersion, int endLogVersion) throws Exception {
        // given
        setupLogFiles(endLogVersion, logFile(start(), commit(1), checkPoint()));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, false, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void twoLogFilesSecondIsCorruptedBeforeCommit(int startLogVersion, int endLogVersion) throws Exception {
        setupLogFiles(endLogVersion, logFile(checkPoint()), logFile(start(), commit(2)));

        Path highestLogFile = logFiles.getLogFile().getHighestLogFile();
        fs.truncate(highestLogFile, fs.getFileSize(highestLogFile) - 3);

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, true, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void twoLogFilesSecondIsCorruptedBeforeAfterCommit(int startLogVersion, int endLogVersion) throws Exception {
        int firstTxId = 2;
        setupLogFiles(endLogVersion, logFile(checkPoint()), logFile(start(), commit(firstTxId), start(), commit(3)));

        Path highestLogFile = logFiles.getLogFile().getHighestLogFile();
        fs.truncate(highestLogFile, fs.getFileSize(highestLogFile) - 3);

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, true, firstTxId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void latestLogFileContainingACheckPointAndAStartAfter(int startLogVersion, int endLogVersion) throws Exception {
        // given
        long txId = 35;
        StartEntry start = start();
        setupLogFiles(endLogVersion, logFile(start, commit(txId), checkPoint(start)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void latestLogFileContainingMultipleCheckPointsOneStartInBetween(int startLogVersion, int endLogVersion)
            throws Exception {
        // given
        setupLogFiles(endLogVersion, logFile(checkPoint(), start(), commit(1), checkPoint()));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, false, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void latestLogFileContainingMultipleCheckPointsOneStartAfterBoth(int startLogVersion, int endLogVersion)
            throws Exception {
        // given
        long txId = 11;
        setupLogFiles(endLogVersion, logFile(checkPoint(), checkPoint(), start(), commit(txId)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void olderLogFileContainingACheckPointAndNewerFileContainingAStart(int startLogVersion, int endLogVersion)
            throws Exception {
        // given
        long txId = 11;
        StartEntry start = start();
        setupLogFiles(endLogVersion, logFile(checkPoint()), logFile(start, commit(txId)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void olderLogFileContainingACheckPointAndNewerFileIsEmpty(int startLogVersion, int endLogVersion) throws Exception {
        // given
        StartEntry start = start();
        setupLogFiles(endLogVersion, logFile(start, commit(1), checkPoint()), logFile());

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, false, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void olderLogFileContainingAStartAndNewerFileContainingACheckPointPointingToAPreviousPositionThanStart(
            int startLogVersion, int endLogVersion) throws Exception {
        // given
        long txId = 123;
        StartEntry start = start();
        setupLogFiles(endLogVersion, logFile(start, commit(txId)), logFile(checkPoint(start)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void olderLogFileContainingAStartAndNewerFileContainingACheckPointPointingToAPreviousPositionThanStartWithoutCommit(
            int startLogVersion, int endLogVersion) throws Exception {
        // given
        StartEntry start = start();
        PositionEntry position = position();
        setupLogFiles(endLogVersion, logFile(start, position), logFile(checkPoint(position)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, false, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void olderLogFileContainingAStartAndNewerFileContainingACheckPointPointingToALaterPositionThanStart(
            int startLogVersion, int endLogVersion) throws Exception {
        // given
        PositionEntry position = position();
        setupLogFiles(endLogVersion, logFile(start(), commit(3), position), logFile(checkPoint(position)));

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, false, NO_TRANSACTION_ID, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void latestLogEmptyStartEntryBeforeAndAfterCheckPointInTheLastButOneLog(int startLogVersion, int endLogVersion)
            throws Exception {
        // given
        long txId = 432;
        setupLogFiles(endLogVersion, logFile(start(), commit(1), checkPoint(), start(), commit(txId)), logFile());

        // when
        var logTailInformation = logFiles.getTailMetadata();

        // then
        assertLatestCheckPoint(true, true, txId, false, logTailInformation);
    }

    @ParameterizedTest
    @MethodSource("params")
    void printProgress(long startLogVersion, long endLogVersion) throws Exception {
        // given
        long txId = 6;
        PositionEntry position = position();
        setupLogFiles(
                endLogVersion,
                logFile(start(), commit(txId - 1), position),
                logFile(checkPoint(position)),
                logFile(start(), commit(txId)));

        // when
        logFiles.getTailMetadata();

        // then
        String message = "Scanning log file with version %d for checkpoint entries";
        assertThat(logProvider).forLevel(INFO).containsMessageWithArguments(message, endLogVersion);
        assertThat(logProvider).forLevel(INFO).containsMessageWithArguments(message, startLogVersion);
    }

    @Test
    void extractTxIdFromFirstChunkEndOnEmptyLogs() throws Exception {
        long chunkTxId = 42;
        setupLogFiles(10, logFile(start(), chunkEnd(chunkTxId)), logFile());

        LogTailMetadata tailMetadata = logFiles.getTailMetadata();
        assertEquals(chunkTxId, ((LogTailInformation) tailMetadata).firstTxIdAfterLastCheckPoint);
    }

    @Test
    void extractTxIdFromFirstChunkEndOnNotEmptyLogs() throws Exception {
        long chunkTxId = 42;
        PositionEntry position = position();
        setupLogFiles(
                11,
                logFile(start(), commit(chunkTxId - 1), position),
                logFile(checkPoint(position)),
                logFile(start(), chunkEnd(chunkTxId)));

        LogTailMetadata tailMetadata = logFiles.getTailMetadata();
        assertEquals(chunkTxId, ((LogTailInformation) tailMetadata).firstTxIdAfterLastCheckPoint);
    }

    // === Below is code for helping the tests above ===

    void setupLogFiles(long endLogVersion, LogCreator... logFiles) throws Exception {
        Map<Entry, LogPosition> positions = new HashMap<>();
        long version = endLogVersion - logFiles.length;
        for (LogCreator logFile : logFiles) {
            logFile.create(++version, positions);
        }

        this.logFiles = createLogFiles();
    }

    LogCreator logFile(Entry... entries) {
        return (logVersion, positions) -> {
            try {
                AtomicLong lastTxId = new AtomicLong();
                logVersionRepository.setCurrentLogVersion(logVersion);
                logVersionRepository.setCheckpointLogVersion(logVersion);
                LifeSupport logFileLife = new LifeSupport();
                logFileLife.start();
                logFileLife.add(logFiles);
                LogFile logFile = logFiles.getLogFile();
                var checkpointFile = logFiles.getCheckpointFile();
                int previousChecksum = BASE_TX_CHECKSUM;
                try {
                    TransactionLogWriter logWriter = logFile.getTransactionLogWriter();
                    LogEntryWriter<?> writer = logWriter.getWriter();
                    for (Entry entry : entries) {
                        LogPosition currentPosition = logWriter.getCurrentPosition();
                        positions.put(entry, currentPosition);
                        if (entry instanceof StartEntry) {
                            writer.writeStartEntry(LATEST_KERNEL_VERSION, 0, 0, previousChecksum, EMPTY_BYTE_ARRAY);
                        } else if (entry instanceof CommitEntry commitEntry) {
                            previousChecksum = writer.writeCommitEntry(LATEST_KERNEL_VERSION, commitEntry.txId, 0);
                            lastTxId.set(commitEntry.txId);
                        } else if (entry instanceof ChunkEndEntry chunkEntry) {
                            previousChecksum = writer.writeChunkEndEntry(LATEST_KERNEL_VERSION, chunkEntry.txId, 1);
                        } else if (entry instanceof CheckPointEntry checkPointEntry) {
                            Entry target = checkPointEntry.withPositionOfEntry;
                            LogPosition logPosition = target != null ? positions.get(target) : currentPosition;
                            assert logPosition != null : "No registered log position for " + target;
                            writeCheckpoint(checkpointFile, UNKNOWN_TRANSACTION_ID, logPosition);
                        } else if (entry instanceof PositionEntry) {
                            // Don't write anything, this entry is just for registering a position so that
                            // another CheckPointEntry can refer to it
                        } else {
                            throw new IllegalArgumentException("Unknown entry " + entry);
                        }
                    }
                } finally {
                    logFileLife.shutdown();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    @FunctionalInterface
    interface LogCreator {
        void create(long version, Map<Entry, LogPosition> positions);
    }

    // Marker interface, helping compilation/test creation
    interface Entry {}

    static StartEntry start() {
        return new StartEntry();
    }

    static CommitEntry commit(long txId) {
        return new CommitEntry(txId);
    }

    static ChunkEndEntry chunkEnd(long txId) {
        return new ChunkEndEntry(txId);
    }

    static CheckPointEntry checkPoint() {
        return checkPoint(null /*means self-position*/);
    }

    static CheckPointEntry checkPoint(Entry forEntry) {
        return new CheckPointEntry(forEntry);
    }

    static PositionEntry position() {
        return new PositionEntry();
    }

    private static class StartEntry implements Entry {}

    private static class CommitEntry implements Entry {
        final long txId;

        CommitEntry(long txId) {
            this.txId = txId;
        }
    }

    private static class ChunkEndEntry implements Entry {
        final long txId;

        ChunkEndEntry(long txId) {
            this.txId = txId;
        }
    }

    private static class CheckPointEntry implements Entry {
        final Entry withPositionOfEntry;

        CheckPointEntry(Entry withPositionOfEntry) {
            this.withPositionOfEntry = withPositionOfEntry;
        }
    }

    static class PositionEntry implements Entry {}

    private static void assertLatestCheckPoint(
            boolean hasCheckPointEntry,
            boolean commitsAfterLastCheckPoint,
            long firstTxIdAfterLastCheckPoint,
            boolean filesNotFound,
            LogTailMetadata logTailInformation) {
        var tail = (LogTailInformation) logTailInformation;
        assertEquals(hasCheckPointEntry, tail.lastCheckPoint != null);
        assertEquals(commitsAfterLastCheckPoint, tail.logsAfterLastCheckpoint());
        if (commitsAfterLastCheckPoint) {
            assertEquals(firstTxIdAfterLastCheckPoint, tail.firstTxIdAfterLastCheckPoint);
        }
        assertEquals(filesNotFound, tail.filesNotFound);
    }
}
