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
package org.neo4j.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.neo4j.logging.log4j.LoggerTarget;

/**
 * An abstract {@link InternalLogProvider} implementation, which ensures {@link InternalLog}s are cached and reused.
 */
public abstract class AbstractLogProvider<T extends InternalLog> implements InternalLogProvider {
    private final ConcurrentHashMap<String, LogWithContext> logCache = new ConcurrentHashMap<>();
    // read-lock: getting log instances, write-lock: changing log level settings
    private final ReadWriteLock settingsChangeLock = new ReentrantReadWriteLock();

    @Override
    public T getLog(final Class<?> loggingClass) {
        return getLog(loggingClass.getName(), () -> buildLog(loggingClass));
    }

    @Override
    public T getLog(final String name) {
        return getLog(name, () -> buildLog(name));
    }

    @Override
    public T getLog(LoggerTarget target) {
        String name = target.getTarget();
        return getLog(name, () -> buildLog(name));
    }

    private T getLog(String name, Supplier<T> logSupplier) {
        // First an optimistic map get
        LogWithContext log = logCache.get(name);
        if (log == null) {
            // Do this locking here around computeIfAbsent because we want both the construction of the log
            // and the placement of it in the map to be under the lock
            settingsChangeLock.readLock().lock();
            try {
                log = logCache.computeIfAbsent(name, c -> new LogWithContext(logSupplier.get(), c));
            } finally {
                settingsChangeLock.readLock().unlock();
            }
        }
        return log.log;
    }

    /**
     * @param loggingClass the context for the returned {@link InternalLog}
     * @return a {@link InternalLog} that logs messages with the {@code loggingClass} as the context
     */
    protected abstract T buildLog(Class<?> loggingClass);

    /**
     * @param name the context for the returned {@link InternalLog}
     * @return a {@link InternalLog} that logs messages with the specified name as the context
     */
    protected abstract T buildLog(String name);

    /**
     * A log accompanied its original context, since logs may be instantiated with a modified version of the context
     * and determining things like log level must be done on the original context.
     */
    private class LogWithContext {
        private final T log;
        private final String fullContext;

        LogWithContext(T log, String fullContext) {
            this.log = log;
            this.fullContext = fullContext;
        }
    }
}
