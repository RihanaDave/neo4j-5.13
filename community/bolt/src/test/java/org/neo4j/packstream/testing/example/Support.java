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
package org.neo4j.packstream.testing.example;

import java.util.Arrays;
import java.util.List;
import org.neo4j.values.storable.TextArray;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.NodeValue;
import org.neo4j.values.virtual.PathValue;
import org.neo4j.values.virtual.RelationshipValue;
import org.neo4j.values.virtual.VirtualValues;

public final class Support {

    private Support() {}

    static final TextArray NO_LABELS = Values.stringArray();
    static final MapValue NO_PROPERTIES = VirtualValues.EMPTY_MAP;

    // Helper to produce literal list of nodes
    public static NodeValue[] nodes(NodeValue... nodes) {
        return nodes;
    }

    // Helper to extract list of nodes from a path
    public static List<NodeValue> nodes(PathValue path) {
        return Arrays.asList(path.nodes());
    }

    // Helper to produce literal list of relationships
    public static RelationshipValue[] edges(RelationshipValue... edgeValues) {
        return edgeValues;
    }
}
