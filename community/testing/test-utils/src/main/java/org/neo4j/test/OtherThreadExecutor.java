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
package org.neo4j.test;

import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.Closeable;
import java.io.PrintStream;
import java.lang.Thread.State;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;

/**
 * Executes commands in another thread. Very useful for writing
 * tests which handles two simultaneous transactions and interleave them,
 * f.ex for testing locking and data visibility.
 */
public class OtherThreadExecutor implements ThreadFactory, Closeable {
    private final ExecutorService commandExecutor = newSingleThreadExecutor(this);
    private volatile Thread thread;
    private volatile ExecutionState executionState;
    private final String name;
    private final long timeoutNanos;

    private static final class AnyThreadState implements Predicate<Thread> {
        private final EnumSet<State> possibleStates;
        private final EnumSet<State> seenStates = EnumSet.noneOf(State.class);

        private AnyThreadState(State... possibleStates) {
            this.possibleStates = EnumSet.noneOf(State.class);
            this.possibleStates.addAll(Arrays.asList(possibleStates));
        }

        @Override
        public boolean test(Thread thread) {
            State threadState = thread.getState();
            seenStates.add(threadState);
            return possibleStates.contains(threadState);
        }

        @Override
        public String toString() {
            return "Any of thread states " + possibleStates + ", but saw " + seenStates;
        }
    }

    private enum ExecutionState {
        REQUESTED_EXECUTION,
        EXECUTING,
        EXECUTED
    }

    public OtherThreadExecutor(String name) {
        this(name, 1, MINUTES);
    }

    public OtherThreadExecutor(String name, long timeout, TimeUnit unit) {
        this.name = name;
        this.timeoutNanos = NANOSECONDS.convert(timeout, unit);
    }

    public <R> Future<R> executeDontWait(final Callable<R> cmd) {
        executionState = ExecutionState.REQUESTED_EXECUTION;
        return commandExecutor.submit(() -> {
            executionState = ExecutionState.EXECUTING;
            try {
                return cmd.call();
            } finally {
                executionState = ExecutionState.EXECUTED;
            }
        });
    }

    public <R> R execute(Callable<R> cmd) throws Exception {
        return executeDontWait(cmd).get();
    }

    public <R> R execute(Callable<R> cmd, long timeout, TimeUnit unit) throws Exception {
        Future<R> future = executeDontWait(cmd);
        boolean success = false;
        try {
            awaitStartExecuting();
            R result = future.get(timeout, unit);
            success = true;
            return result;
        } finally {
            if (!success) {
                future.cancel(true);
            }
        }
    }

    public void awaitStartExecuting() throws InterruptedException {
        while (executionState == ExecutionState.REQUESTED_EXECUTION) {
            Thread.sleep(10);
        }
    }

    public <R> R awaitFuture(Future<R> future) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeoutNanos, NANOSECONDS);
    }

    public interface WorkerCommand<R> {
        R doWork() throws Exception;
    }

    public static <R> Callable<R> command(Race.ThrowingRunnable runnable) {
        return () -> {
            try {
                runnable.run();
                return null;
            } catch (Exception e) {
                throw e;
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread newThread = new Thread(r, getClass().getName() + ":" + name) {
            @Override
            public void run() {
                try {
                    super.run();
                } finally {
                    OtherThreadExecutor.this.thread = null;
                }
            }
        };
        this.thread = newThread;
        return newThread;
    }

    @Override
    public String toString() {
        Thread thread = this.thread;
        return format("%s[%s,state=%s]", getClass().getSimpleName(), name, thread == null ? "dead" : thread.getState());
    }

    public WaitDetails waitUntilWaiting() throws TimeoutException {
        return waitUntilWaiting(details -> true);
    }

    public WaitDetails waitUntilBlocked() throws TimeoutException {
        return waitUntilBlocked(details -> true);
    }

    public WaitDetails waitUntilWaiting(Predicate<WaitDetails> correctWait) throws TimeoutException {
        return waitUntilThreadState(correctWait, Thread.State.WAITING, Thread.State.TIMED_WAITING);
    }

    public WaitDetails waitUntilBlocked(Predicate<WaitDetails> correctWait) throws TimeoutException {
        return waitUntilThreadState(correctWait, Thread.State.BLOCKED);
    }

    public WaitDetails waitUntilThreadState(final Thread.State... possibleStates) throws TimeoutException {
        return waitUntilThreadState(details -> true, possibleStates);
    }

    public WaitDetails waitUntilThreadState(Predicate<WaitDetails> correctWait, final Thread.State... possibleStates)
            throws TimeoutException {
        long endTimeNanos = nanoTime() + timeoutNanos;
        WaitDetails details;
        while (!correctWait.test(details = waitUntil(new AnyThreadState(possibleStates)))) {
            LockSupport.parkNanos(MILLISECONDS.toNanos(20));
            if (nanoTime() > endTimeNanos) {
                throw new TimeoutException("Wanted to wait for any of " + Arrays.toString(possibleStates) + " over at "
                        + correctWait + ", but didn't managed to get there in " + timeoutNanos + "ns. "
                        + "instead ended up waiting in "
                        + details);
            }
        }
        return details;
    }

    public WaitDetails waitUntil(Predicate<Thread> condition) throws TimeoutException {
        long end = nanoTime() + timeoutNanos;
        Thread thread = getThread();
        while (!condition.test(thread) || executionState == ExecutionState.REQUESTED_EXECUTION) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // whatever
            }

            if (nanoTime() > end) {
                throw new TimeoutException("The executor didn't meet condition '" + condition
                        + "' inside an executing command for " + timeoutNanos + " ns");
            }
        }

        if (executionState == ExecutionState.EXECUTED) {
            throw new IllegalStateException("Would have wanted " + thread + " to wait for " + condition
                    + " but that never happened within the duration of executed task");
        }

        return new WaitDetails(thread.getStackTrace());
    }

    public static class WaitDetails {
        private final StackTraceElement[] stackTrace;

        public WaitDetails(StackTraceElement[] stackTrace) {
            this.stackTrace = stackTrace;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (StackTraceElement element : stackTrace) {
                builder.append(format(element + "%n"));
            }
            return builder.toString();
        }

        public boolean isAt(Class<?> clz, String method) {
            for (StackTraceElement element : stackTrace) {
                if (element.getClassName().equals(clz.getName())
                        && element.getMethodName().equals(method)) {
                    return true;
                }
            }
            return false;
        }
    }

    public Thread.State state() {
        return thread.getState();
    }

    private Thread getThread() {
        Thread thread = null;
        while (thread == null) {
            thread = this.thread;
        }
        return thread;
    }

    @Override
    public void close() {
        commandExecutor.shutdown();
        try {
            commandExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // shutdownNow() will interrupt running tasks if necessary
        }
        if (!commandExecutor.isTerminated()) {
            commandExecutor.shutdownNow();
        }
    }

    public void interrupt() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    public void printStackTrace(PrintStream out) {
        Thread thread = getThread();
        out.println(thread);
        for (StackTraceElement trace : thread.getStackTrace()) {
            out.println("\tat " + trace);
        }
    }
}
