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
package org.neo4j.dbms.identity;

import java.io.IOException;
import java.util.UUID;
import org.neo4j.io.fs.ReadableChannel;
import org.neo4j.io.fs.WritableChannel;
import org.neo4j.io.marshal.SafeStateMarshal;

public class ServerIdMarshal extends SafeStateMarshal<ServerId> {
    public static final ServerIdMarshal INSTANCE = new ServerIdMarshal();

    private ServerIdMarshal() {
        // use INSTANCE
    }

    @Override
    public void marshal(ServerId serverId, WritableChannel channel) throws IOException {
        if (serverId == null) {
            channel.put((byte) 0);
        } else {
            channel.put((byte) 1);
            channel.putLong(serverId.uuid().getMostSignificantBits());
            channel.putLong(serverId.uuid().getLeastSignificantBits());
        }
    }

    @Override
    public ServerId unmarshal0(ReadableChannel channel) throws IOException {
        byte nullMarker = channel.get();
        if (nullMarker == 0) {
            return null;
        } else {
            long mostSigBits = channel.getLong();
            long leastSigBits = channel.getLong();
            return new ServerId(new UUID(mostSigBits, leastSigBits));
        }
    }

    @Override
    public ServerId startState() {
        return null;
    }

    @Override
    public long ordinal(ServerId serverId) {
        return serverId == null ? 0 : 1;
    }
}
