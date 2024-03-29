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
package org.neo4j.internal.batchimport.input.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.neo4j.csv.reader.Configuration;
import org.neo4j.csv.reader.Extractors;

class StringDeserializationTest {
    private final Configuration configuration = Configuration.COMMAS;
    private final Extractors extractors = new Extractors(configuration.arrayDelimiter());
    private final Header.Entry entry1 = new Header.Entry(null, Type.START_ID, null, extractors.int_());
    private final Header.Entry entry2 = new Header.Entry(null, Type.TYPE, null, extractors.string());
    private final Header.Entry entry3 = new Header.Entry(null, Type.END_ID, null, extractors.int_());

    @Test
    void shouldProvideDelimiterAfterFirstEmptyField() {
        // given
        StringDeserialization deserialization = new StringDeserialization(configuration);

        // when
        deserialization.handle(entry1, null);
        deserialization.handle(entry2, "MyType");
        deserialization.handle(entry3, 123);
        String line = deserialization.materialize();

        // then
        assertEquals(line, ",MyType,123");
    }

    @Test
    void shouldProvideDelimiterBeforeLastEmptyField() {
        // given
        StringDeserialization deserialization = new StringDeserialization(configuration);

        // when
        deserialization.handle(entry1, 123);
        deserialization.handle(entry2, "MyType");
        deserialization.handle(entry3, null);
        String line = deserialization.materialize();

        // then
        assertEquals(line, "123,MyType,");
    }
}
