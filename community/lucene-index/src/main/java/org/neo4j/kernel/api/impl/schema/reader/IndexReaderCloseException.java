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
package org.neo4j.kernel.api.impl.schema.reader;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Exception that will be thrown in case if there was a problem during index reader close
 */
public class IndexReaderCloseException extends UncheckedIOException {
    private IndexReaderCloseException(String message, IOException cause) {
        super(message, cause);
    }

    public IndexReaderCloseException(IOException cause) {
        super(cause);
    }

    public IndexReaderCloseException(String message, Throwable throwable) {
        this(message, throwable instanceof IOException ? ((IOException) throwable) : new IOException(throwable));
    }
}
