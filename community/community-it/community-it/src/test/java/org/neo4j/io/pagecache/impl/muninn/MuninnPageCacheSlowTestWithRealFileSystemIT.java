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
package org.neo4j.io.pagecache.impl.muninn;

import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

@TestDirectoryExtension
class MuninnPageCacheSlowTestWithRealFileSystemIT extends MuninnPageCacheSlowIT {
    @Inject
    TestDirectory directory;

    MuninnPageCacheSlowTestWithRealFileSystemIT() {
        SHORT_TIMEOUT_MILLIS = 120_000;
        SEMI_LONG_TIMEOUT_MILLIS = 360_000;
        LONG_TIMEOUT_MILLIS = 1_200_000;
    }

    @Override
    protected Fixture<MuninnPageCache> createFixture() {
        return super.createFixture()
                .withFileSystemAbstraction(DefaultFileSystemAbstraction::new)
                .withFileConstructor(directory::file);
    }
}