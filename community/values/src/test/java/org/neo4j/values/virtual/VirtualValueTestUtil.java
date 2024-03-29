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
package org.neo4j.values.virtual;

import static org.neo4j.values.storable.Values.stringArray;
import static org.neo4j.values.storable.Values.stringValue;
import static org.neo4j.values.virtual.VirtualValues.EMPTY_MAP;
import static org.neo4j.values.virtual.VirtualValues.nodeValue;
import static org.neo4j.values.virtual.VirtualValues.relationshipValue;

import java.util.Arrays;
import org.neo4j.values.AnyValue;
import org.neo4j.values.VirtualValue;
import org.neo4j.values.storable.TextArray;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Values;

@SuppressWarnings("WeakerAccess")
public class VirtualValueTestUtil {
    public static AnyValue toAnyValue(Object o) {
        if (o instanceof AnyValue) {
            return (AnyValue) o;
        } else {
            return Values.of(o);
        }
    }

    public static NodeValue node(long id, String... labels) {
        return node(id, stringArray(labels), EMPTY_MAP);
    }

    public static NodeValue node(long id, TextArray labels, MapValue properties) {
        return nodeValue(id, String.valueOf(id), labels, properties);
    }

    public static VirtualValue path(VirtualValue... pathElements) {
        assert pathElements.length % 2 == 1;
        NodeValue[] nodes = new NodeValue[pathElements.length / 2 + 1];
        RelationshipValue[] rels = new RelationshipValue[pathElements.length / 2];
        nodes[0] = (NodeValue) pathElements[0];
        for (int i = 1; i < pathElements.length; i += 2) {
            rels[i / 2] = (RelationshipValue) pathElements[i];
            nodes[i / 2 + 1] = (NodeValue) pathElements[i + 1];
        }
        return VirtualValues.path(nodes, rels);
    }

    public static RelationshipValue rel(long id, long start, long end) {
        return rel(id, node(start), node(end), stringValue("T"), EMPTY_MAP);
    }

    public static RelationshipValue rel(
            long id, VirtualNodeReference start, VirtualNodeReference end, TextValue type, MapValue properties) {
        return relationshipValue(id, String.valueOf(id), start, end, type, properties);
    }

    public static ListValue list(Object... objects) {
        AnyValue[] values = new AnyValue[objects.length];
        for (int i = 0; i < objects.length; i++) {
            values[i] = toAnyValue(objects[i]);
        }
        return VirtualValues.list(values);
    }

    public static MapValue map(Object... keyOrVal) {
        assert keyOrVal.length % 2 == 0;
        String[] keys = new String[keyOrVal.length / 2];
        AnyValue[] values = new AnyValue[keyOrVal.length / 2];
        for (int i = 0; i < keyOrVal.length; i += 2) {
            keys[i / 2] = (String) keyOrVal[i];
            values[i / 2] = toAnyValue(keyOrVal[i + 1]);
        }
        return VirtualValues.map(keys, values);
    }

    public static NodeValue[] nodes(long... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> nodeValue(id, String.valueOf(id), stringArray("L"), EMPTY_MAP))
                .toArray(NodeValue[]::new);
    }

    public static RelationshipValue[] relationships(long... ids) {
        return Arrays.stream(ids).mapToObj(id -> rel(id, 0, 1)).toArray(RelationshipValue[]::new);
    }

    public static String singleLongToElementId(long id) {
        return String.valueOf(id);
    }
}
