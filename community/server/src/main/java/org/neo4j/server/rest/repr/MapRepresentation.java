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
package org.neo4j.server.rest.repr;

import static java.lang.reflect.Array.get;
import static java.lang.reflect.Array.getLength;
import static java.util.Arrays.asList;

import java.util.Map;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;

public class MapRepresentation extends MappingRepresentation {

    private final Map<String, Object> value;

    public MapRepresentation(Map value) {
        super(RepresentationType.MAP);
        this.value = value;
    }

    @Override
    protected void serialize(MappingSerializer serializer) {
        for (var entry : value.entrySet()) {
            Object val = entry.getValue();
            String keyString = entry.getKey() == null ? "null" : entry.getKey();
            if (val instanceof Number) {
                serializer.putNumber(keyString, (Number) val);
            } else if (val instanceof Boolean) {
                serializer.putBoolean(keyString, (Boolean) val);
            } else if (val instanceof String) {
                serializer.putString(keyString, (String) val);
            } else if (val instanceof Path) {
                PathRepresentation<Path> representation = new PathRepresentation<>((Path) val);
                serializer.putMapping(keyString, representation);
            } else if (val instanceof Iterable) {
                serializer.putList(keyString, ObjectToRepresentationConverter.getListRepresentation((Iterable) val));
            } else if (val instanceof Map) {
                serializer.putMapping(keyString, ObjectToRepresentationConverter.getMapRepresentation((Map) val));
            } else if (val == null) {
                serializer.putString(keyString, null);
            } else if (val.getClass().isArray()) {
                Object[] objects = toArray(val);

                serializer.putList(keyString, ObjectToRepresentationConverter.getListRepresentation(asList(objects)));
            } else if (val instanceof Node || val instanceof Relationship) {
                Representation representation = ObjectToRepresentationConverter.getSingleRepresentation(val);
                serializer.putMapping(keyString, (MappingRepresentation) representation);
            } else {
                throw new IllegalArgumentException("Unsupported value type: " + val.getClass());
            }
        }
    }

    private static Object[] toArray(Object val) {
        int length = getLength(val);

        Object[] objects = new Object[length];

        for (int i = 0; i < length; i++) {
            objects[i] = get(val, i);
        }

        return objects;
    }
}
