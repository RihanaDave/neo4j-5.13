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
package org.neo4j.io.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.io.fs.FileSystemAbstraction.INVALID_FILE_DESCRIPTOR;

import java.io.IOException;
import org.junit.jupiter.api.Test;

public class EphemeralFileSystemAbstractionTest extends FileSystemAbstractionTest {
    @Override
    protected FileSystemAbstraction buildFileSystemAbstraction() {
        return new EphemeralFileSystemAbstraction();
    }

    @Test
    void ephemeralFileSystemFileDescriptors() throws IOException {
        fsa.mkdirs(path);
        assertTrue(fsa.fileExists(path));
        path = path.resolve("some_file");
        try (StoreChannel channel = fsa.write(path)) {
            assertEquals(INVALID_FILE_DESCRIPTOR, fsa.getFileDescriptor(channel));
        }
    }
}
