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

import java.util.List;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.ValueRepresentation;

/**
 * This class is way too similar to org.neo4j.collection.PrimitiveArrays.
 * <p>
 * Should we introduce dependency on primitive collections?
 */
final class ArrayHelpers {
    private ArrayHelpers() {}

    static boolean containsNull(AnyValue[] values) {
        for (AnyValue value : values) {
            if (value == null) {
                return true;
            }
        }
        return false;
    }

    static boolean containsNull(List<AnyValue> values) {
        for (AnyValue value : values) {
            if (value == null) {
                return true;
            }
        }
        return false;
    }

    static boolean assertValueRepresentation(AnyValue[] values, ValueRepresentation representation) {
        ValueRepresentation actual = ValueRepresentation.ANYTHING;
        for (AnyValue value : values) {
            actual = actual.coerce(value.valueRepresentation());
        }
        return actual == representation; // TODO reviewer: should we require anything here?
    }
}
