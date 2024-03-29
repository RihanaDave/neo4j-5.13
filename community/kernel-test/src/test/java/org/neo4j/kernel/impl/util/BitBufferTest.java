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
package org.neo4j.kernel.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.util.BitBuffer.bits;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.neo4j.util.BitBuffer;
import org.neo4j.util.BitUtils;

class BitBufferTest {
    @Test
    void asBytes() {
        int numberOfBytes = 14;
        BitBuffer bits = bits(numberOfBytes);
        for (byte i = 0; i < numberOfBytes; i++) {
            bits.put(i);
        }

        byte[] bytes = bits.asBytes();
        for (byte i = 0; i < numberOfBytes; i++) {
            assertEquals(i, bytes[i]);
        }
    }

    @Test
    void doubleAsBytes() {
        double[] array1 = {1.0, 2.0, 3.0, 4.0, 5.0};
        BitBuffer bits = bits(array1.length * 8);
        for (double value : array1) {
            bits.put(Double.doubleToRawLongBits(value));
        }
        String first = bits.toString();
        byte[] asBytes = bits.asBytes();
        String other = BitBuffer.bitsFromBytes(asBytes).toString();
        assertEquals(first, other);
    }

    @Test
    void doubleAsBytesWithOffset() {
        double[] array1 = {1.0, 2.0, 3.0, 4.0, 5.0};
        BitBuffer bits = bits(array1.length * 8);
        for (double value : array1) {
            bits.put(Double.doubleToRawLongBits(value));
        }
        int offset = 6;
        byte[] asBytesOffset = bits.asBytes(offset);
        byte[] asBytes = bits.asBytes();
        assertEquals(asBytes.length, array1.length * 8);
        assertEquals(asBytesOffset.length, array1.length * 8 + offset);
        for (int i = 0; i < asBytes.length; i++) {
            assertEquals(asBytesOffset[i + offset], asBytes[i]);
        }
    }

    @Test
    void writeAndRead() {
        for (int b = 5; b <= 8; b++) {
            BitBuffer bits = bits(16);
            for (byte value = 0; value < 16; value++) {
                bits.put(value, b);
            }
            for (byte expected = 0; bits.available(); expected++) {
                assertEquals(expected, bits.getByte(b));
            }
        }

        for (byte value = Byte.MIN_VALUE; value < Byte.MAX_VALUE; value++) {
            BitBuffer bits = bits(8);
            bits.put(value);
            assertEquals(value, bits.getByte());
        }
    }

    @Test
    void writeAndReadByteBuffer() {
        byte[] bytes = new byte[512];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(123456789L);
        buffer.flip();
        BitBuffer bits = BitBuffer.bitsFromBytes(bytes, 0, buffer.limit());

        assertEquals(123456789L, bits.getLong());
    }

    @Test
    void numberToStringSeparatesAfter8Bits() {
        StringBuilder builder = new StringBuilder();
        BitUtils.numberToString(builder, 0b11111111, 2);
        assertThat(builder.toString()).isEqualTo("[00000000,11111111]");
    }
}
