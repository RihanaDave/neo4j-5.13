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
package org.neo4j.bolt.protocol.common.connection;

import org.neo4j.test.extension.actors.ActorsExtension;

@ActorsExtension
class DefaultBoltConnectionTest {
    //    private final AssertableLogProvider logProvider = new AssertableLogProvider();
    //    private final LogService logService = new SimpleLogService(logProvider);
    //    private final BoltConnectionLifetimeListener connectionListener = mock(BoltConnectionLifetimeListener.class);
    //    private final BoltConnectionQueueMonitor queueMonitor = mock(BoltConnectionQueueMonitor.class);
    //    private final EmbeddedChannel channel = new EmbeddedChannel();
    //
    //    private BoltChannel boltChannel;
    //    private StateMachine stateMachine;
    //
    //    @Inject
    //    Actor otherThread;
    //
    //    @BeforeEach
    //    void setup() {
    //        boltChannel = newTestBoltChannel(channel);
    //        stateMachine = mock(StateMachine.class);
    //        when(stateMachine.shouldStickOnThread()).thenReturn(false);
    //        when(stateMachine.hasOpenStatement()).thenReturn(false);
    //    }
    //
    //    @AfterEach
    //    void cleanup() {
    //        channel.finishAndReleaseAll();
    //    }
    //
    //    @Test
    //    void idShouldReturnBoltChannelId() {
    //        BoltConnection connection = newConnection();
    //
    //        assertEquals(boltChannel.id(), connection.id());
    //    }
    //
    //    @Test
    //    void localAddressShouldReturnBoltServerAddress() {
    //        BoltConnection connection = newConnection();
    //
    //        assertEquals(boltChannel.serverAddress(), connection.localAddress());
    //    }
    //
    //    @Test
    //    void remoteAddressShouldReturnBoltClientAddress() {
    //        BoltConnection connection = newConnection();
    //
    //        assertEquals(boltChannel.clientAddress(), connection.remoteAddress());
    //    }
    //
    //    @Test
    //    void channelShouldReturnBoltRawChannel() {
    //        BoltConnection connection = newConnection();
    //
    //        assertEquals(boltChannel.rawChannel(), connection.channel());
    //    }
    //
    //    @Test
    //    void hasPendingJobsShouldReportFalseWhenInitialised() {
    //        BoltConnection connection = newConnection();
    //
    //        assertFalse(connection.hasPendingJobs());
    //    }
    //
    //    @Test
    //    void startShouldNotifyListener() {
    //        BoltConnection connection = newConnection();
    //
    //        connection.start();
    //
    //        verify(connectionListener).created(connection);
    //    }
    //
    //    @Test
    //    void stopShouldNotifyListenerOnTheNextBatch() {
    //        BoltConnection connection = newConnection();
    //        connection.start();
    //
    //        connection.stop();
    //        connection.processNextBatch();
    //
    //        verify(connectionListener).closed(connection);
    //    }
    //
    //    @Test
    //    void enqueuedShouldNotifyQueueMonitor() {
    //        Job job = Jobs.noop();
    //        BoltConnection connection = newConnection();
    //
    //        connection.enqueue(job);
    //
    //        verify(queueMonitor).enqueued(eq(connection), any());
    //    }
    //
    //    @Test
    //    void enqueuedShouldQueueJob() {
    //        Job job = Jobs.noop();
    //        BoltConnection connection = newConnection();
    //
    //        connection.enqueue(job);
    //
    //        assertTrue(connection.hasPendingJobs());
    //    }
    //
    //    @Test
    //    void processNextBatchShouldDoNothingIfQueueIsEmptyAndConnectionNotClosed() {
    //        BoltConnection connection = newConnection();
    //
    //        connection.processNextBatch();
    //
    //        verify(queueMonitor, never()).drained(same(connection), anyCollection());
    //    }
    //
    //    @Test
    //    void processNextBatchShouldNotifyQueueMonitorAboutDrain() {
    //        List<Job> drainedJobs = new ArrayList<>();
    //        Job job = Jobs.noop();
    //        BoltConnection connection = newConnection();
    //        doAnswer(inv -> drainedJobs.addAll(inv.getArgument(1)))
    //                .when(queueMonitor)
    //                .drained(same(connection), anyCollection());
    //
    //        connection.enqueue(job);
    //        connection.processNextBatch();
    //
    //        verify(queueMonitor).drained(same(connection), anyCollection());
    //        assertThat(drainedJobs).hasSize(1);
    //    }
    //
    //    @Test
    //    void processNextBatchShouldDrainMaxBatchSizeItemsOnEachCall() {
    //        List<Job> drainedJobs = new ArrayList<>();
    //        BoltConnection connection = newConnection(10);
    //        doAnswer(inv -> drainedJobs.addAll(inv.getArgument(1)))
    //                .when(queueMonitor)
    //                .drained(same(connection), anyCollection());
    //
    //        for (int i = 0; i < 15; i++) {
    //            Job newJob = Jobs.noop();
    //            connection.enqueue(newJob);
    //        }
    //
    //        connection.processNextBatch();
    //
    //        verify(queueMonitor).drained(same(connection), anyCollection());
    //        assertEquals(10, drainedJobs.size());
    //        assertThat(drainedJobs).hasSize(10);
    //
    //        drainedJobs.clear();
    //        connection.processNextBatch();
    //
    //        verify(queueMonitor, times(2)).drained(same(connection), anyCollection());
    //        assertEquals(5, drainedJobs.size());
    //        assertThat(drainedJobs).hasSize(5);
    //    }
    //
    //    @Test
    //    void interruptShouldInterruptStateMachine() {
    //        BoltConnection connection = newConnection();
    //
    //        connection.interrupt();
    //
    //        verify(stateMachine).interrupt();
    //    }
    //
    //    @Test
    //    void stopShouldFirstMarkStateMachineForTermination() {
    //        BoltConnection connection = newConnection();
    //
    //        connection.stop();
    //
    //        verify(stateMachine).markForTermination();
    //        verify(queueMonitor).enqueued(ArgumentMatchers.eq(connection), any(Job.class));
    //    }
    //
    //    @Test
    //    void stopShouldCloseStateMachineOnProcessNextBatch() {
    //        BoltConnection connection = newConnection();
    //
    //        connection.stop();
    //
    //        connection.processNextBatch();
    //
    //        verify(queueMonitor).enqueued(ArgumentMatchers.eq(connection), any(Job.class));
    //        verify(stateMachine).markForTermination();
    //        verify(stateMachine).close();
    //    }
    //
    //    @Test
    //    void stopShouldCloseStateMachineIfEnqueueEndsWithRejectedExecutionException() {
    //        BoltConnection connection = newConnection();
    //
    //        doAnswer(i -> {
    //                    connection.handleSchedulingError(new RejectedExecutionException());
    //                    return null;
    //                })
    //                .when(queueMonitor)
    //                .enqueued(ArgumentMatchers.eq(connection), any(Job.class));
    //
    //        connection.stop();
    //
    //        verify(stateMachine).markForTermination();
    //        verify(stateMachine).close();
    //    }
    //
    //    @Test
    //    void shouldLogBoltConnectionAuthFatalityError() {
    //        BoltConnection connection = newConnection();
    //        connection.enqueue(machine -> {
    //            throw new BoltConnectionAuthFatality(
    //                    new AuthenticationException(Status.Security.Unauthorized, "inner error"));
    //        });
    //        connection.processNextBatch();
    //        verify(stateMachine).close();
    //        assertThat(logProvider)
    //                .forClass(DefaultBoltConnection.class)
    //                .forLevel(WARN)
    //                .containsMessages("inner error");
    //    }
    //
    //    @Test
    //    void processNextBatchShouldCloseConnectionOnFatalAuthenticationError() {
    //        BoltConnection connection = newConnection();
    //
    //        connection.enqueue(machine -> {
    //            throw new BoltConnectionAuthFatality("auth failure", new RuntimeException("inner error"));
    //        });
    //
    //        connection.processNextBatch();
    //
    //        verify(stateMachine).close();
    //        assertThat(logProvider).doesNotHaveAnyLogs();
    //    }
    //
    //    @Test
    //    void processNextBatchShouldCloseConnectionAndLogOnFatalBoltError() {
    //        BoltConnectionFatality exception = new BoltProtocolBreachFatality("fatal bolt error");
    //        BoltConnection connection = newConnection();
    //
    //        connection.enqueue(machine -> {
    //            throw exception;
    //        });
    //
    //        connection.processNextBatch();
    //
    //        verify(stateMachine).close();
    //        assertThat(logProvider)
    //                .forClass(DefaultBoltConnection.class)
    //                .forLevel(ERROR)
    //                .containsMessageWithException("Protocol breach detected in bolt session", exception);
    //    }
    //
    //    @Test
    //    void processNextBatchShouldCloseConnectionAndLogOnUnexpectedException() {
    //        RuntimeException exception = new RuntimeException("unexpected exception");
    //        BoltConnection connection = newConnection();
    //
    //        connection.enqueue(machine -> {
    //            throw exception;
    //        });
    //
    //        connection.processNextBatch();
    //
    //        verify(stateMachine).close();
    //        assertThat(logProvider)
    //                .forClass(DefaultBoltConnection.class)
    //                .forLevel(ERROR)
    //                .containsMessageWithException("Unexpected error detected in bolt session", exception);
    //    }
    //
    //    @Test
    //    void processNextBatchShouldThrowAssertionErrorIfStatementOpen() {
    //        BoltConnection connection = newConnection(1);
    //        connection.enqueue(Jobs.noop());
    //        connection.enqueue(Jobs.noop());
    //
    //        // force to a message waiting loop
    //        when(stateMachine.hasOpenStatement()).thenReturn(true);
    //
    //        connection.processNextBatch();
    //
    //        assertThat(logProvider)
    //                .forClass(DefaultBoltConnection.class)
    //                .forLevel(ERROR)
    //                .assertExceptionForLogMessage("Unexpected error")
    //                .isInstanceOf(AssertionError.class);
    //    }
    //
    //    @Test
    //    void processNextBatchShouldNotThrowAssertionErrorIfStatementOpenButStopping() {
    //        BoltConnection connection = newConnection(1);
    //        connection.enqueue(Jobs.noop());
    //        connection.enqueue(Jobs.noop());
    //
    //        // force to a message waiting loop
    //        when(stateMachine.hasOpenStatement()).thenReturn(true);
    //
    //        connection.stop();
    //        connection.processNextBatch();
    //
    //        assertThat(logProvider).doesNotHaveAnyLogs();
    //    }
    //
    //    @Test
    //    void processNextBatchShouldReturnWhenConnectionIsStopped() throws Exception {
    //        BoltConnection connection = newConnection(1);
    //        connection.enqueue(Jobs.noop());
    //        connection.enqueue(Jobs.noop());
    //
    //        // force to a message waiting loop
    //        when(stateMachine.shouldStickOnThread()).thenReturn(true);
    //
    //        Future<Boolean> future = otherThread.submit(connection::processNextBatch);
    //
    //        connection.stop();
    //
    //        future.get(1, TimeUnit.MINUTES);
    //
    //        verify(stateMachine).close();
    //    }
    //
    //    @Test
    //    void shouldFlushErrorAndCloseConnectionIfFailedToSchedule() {
    //        // Given
    //        BoltConnection connection = newConnection();
    //
    //        // When
    //        RejectedExecutionException error = new RejectedExecutionException("Failed to schedule");
    //        connection.handleSchedulingError(error);
    //
    //        // Then
    //        verify(stateMachine).markFailed(argThat(e -> e.status().equals(Status.Request.NoThreadsAvailable)));
    //        verify(stateMachine).close();
    //    }
    //
    //    private DefaultBoltConnection newConnection() {
    //        return newConnection(10);
    //    }
    //
    //    private DefaultBoltConnection newConnection(int maxBatchSize) {
    //        return new DefaultBoltConnection(
    //                boltChannel,
    //                stateMachine,
    //                logService,
    //                connectionListener,
    //                queueMonitor,
    //                maxBatchSize,
    //                mock(BoltConnectionMetricsMonitor.class),
    //                Clock.systemUTC());
    //    }
}
