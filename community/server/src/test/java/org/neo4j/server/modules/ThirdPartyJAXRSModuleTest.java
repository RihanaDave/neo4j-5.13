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
package org.neo4j.server.modules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.Config;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.server.CommunityNeoWebServer;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage;
import org.neo4j.server.web.WebServer;

class ThirdPartyJAXRSModuleTest {
    @Test
    void shouldReportThirdPartyPackagesAtSpecifiedMount() throws Exception {
        // Given
        WebServer webServer = mock(WebServer.class);

        CommunityNeoWebServer neoServer = mock(CommunityNeoWebServer.class);
        when(neoServer.getBaseUri()).thenReturn(new URI("http://localhost:7575"));
        when(neoServer.getWebServer()).thenReturn(webServer);

        Config config = mock(Config.class);
        List<ThirdPartyJaxRsPackage> jaxRsPackages = new ArrayList<>();
        String path = "/third/party/package";
        jaxRsPackages.add(new ThirdPartyJaxRsPackage("org.example.neo4j", path));
        when(config.get(ServerSettings.third_party_packages)).thenReturn(jaxRsPackages);

        // When
        ThirdPartyJAXRSModule module = new ThirdPartyJAXRSModule(webServer, config, NullLogProvider.getInstance());
        module.start();

        // Then
        verify(webServer).addJAXRSPackages(any(List.class), anyString(), any());
    }
}
