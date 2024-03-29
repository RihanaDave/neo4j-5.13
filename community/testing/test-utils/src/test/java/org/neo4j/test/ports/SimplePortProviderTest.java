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
package org.neo4j.test.ports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class SimplePortProviderTest {
    @Test
    void shouldProvideUniquePorts() {
        PortProvider portProvider = new SimplePortProvider(port -> false, 42);

        int port1 = portProvider.getNextFreePort("foo");
        int port2 = portProvider.getNextFreePort("foo");

        assertThat(port1).isNotEqualTo(port2);
    }

    @Test
    void shouldSkipOccupiedPorts() {
        PortProbe portProbe = mock(PortProbe.class);
        PortProvider portProvider = new SimplePortProvider(portProbe, 40);

        when(portProbe.isOccupied(40)).thenReturn(false);
        when(portProbe.isOccupied(41)).thenReturn(false);
        when(portProbe.isOccupied(42)).thenReturn(true);
        when(portProbe.isOccupied(43)).thenReturn(false);
        assertThat(portProvider.getNextFreePort("foo")).isEqualTo(40);
        assertThat(portProvider.getNextFreePort("foo")).isEqualTo(41);
        assertThat(portProvider.getNextFreePort("foo")).isEqualTo(43);
    }

    @Test
    void shouldNotOverRun() {
        PortProvider portProvider = new SimplePortProvider(port -> false, 65534);

        portProvider.getNextFreePort("foo");
        portProvider.getNextFreePort("foo");

        try {
            portProvider.getNextFreePort("foo");

            fail("Failure was expected");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("There are no more ports available");
        }
    }
}
