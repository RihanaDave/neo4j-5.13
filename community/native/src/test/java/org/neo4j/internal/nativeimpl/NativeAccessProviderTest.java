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
package org.neo4j.internal.nativeimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class NativeAccessProviderTest {
    @Test
    @EnabledOnOs(OS.LINUX)
    void linuxNativeAccessSelectedOnLinux() {
        NativeAccess nativeAccess = NativeAccessProvider.getNativeAccess();
        assertThat(nativeAccess).isInstanceOf(LinuxNativeAccess.class);
        assertTrue(nativeAccess.isAvailable());
    }

    @Test
    @DisabledOnOs(OS.LINUX)
    void absentNativeAccessSelectedOnNonLinux() {
        NativeAccess nativeAccess = NativeAccessProvider.getNativeAccess();
        assertThat(nativeAccess).isInstanceOf(AbsentNativeAccess.class);
        assertFalse(nativeAccess.isAvailable());
    }
}
