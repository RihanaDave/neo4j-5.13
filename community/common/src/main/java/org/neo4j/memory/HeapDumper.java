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
package org.neo4j.memory;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;

public class HeapDumper {
    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
    private final HotSpotDiagnosticMXBean hotspotDiagnosticMxBean;

    public HeapDumper() {
        hotspotDiagnosticMxBean = getHotspotDiagnosticMxBean();
    }

    public void createHeapDump(String fileName, boolean live) {
        try {
            hotspotDiagnosticMxBean.dumpHeap(fileName, live);
        } catch (IOException e) {
            throw new RuntimeException("file: " + fileName, e);
        }
    }

    private static HotSpotDiagnosticMXBean getHotspotDiagnosticMxBean() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            return ManagementFactory.newPlatformMXBeanProxy(server, HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
        } catch (IOException error) {
            throw new RuntimeException("Failed getting Hotspot Diagnostic MX bean", error);
        }
    }
}
