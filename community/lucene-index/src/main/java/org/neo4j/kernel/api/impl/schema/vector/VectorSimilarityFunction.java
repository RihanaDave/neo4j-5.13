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
package org.neo4j.kernel.api.impl.schema.vector;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.neo4j.values.storable.FloatingPointArray;
import org.neo4j.values.storable.Value;

public enum VectorSimilarityFunction {
    // TODO VECTOR: perhaps some unrolling and/or vector api (when available) could be used here
    //              perhaps investigate some more accurate normalisation techniques

    EUCLIDEAN {
        @Override
        public float[] maybeToValidVector(FloatingPointArray candidate) {
            if (candidate == null || candidate.isEmpty()) {
                return null;
            }

            final var dimensions = candidate.length();
            final var vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                final var element = candidate.floatValue(i);
                if (!Float.isFinite(element)) {
                    return null;
                }
                vector[i] = element;
            }
            return vector;
        }

        @Override
        public float[] maybeToValidVector(List<Double> candidate) {
            if (candidate == null || candidate.isEmpty()) {
                return null;
            }

            final var dimensions = candidate.size();
            final var vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                final var rawElement = candidate.get(i);
                final float element;
                if (rawElement == null || !Float.isFinite(element = rawElement.floatValue())) {
                    return null;
                }
                vector[i] = element;
            }
            return vector;
        }
    },

    COSINE {
        @Override
        public float[] maybeToValidVector(FloatingPointArray candidate) {
            if (candidate == null || candidate.isEmpty()) {
                return null;
            }

            final var dimensions = candidate.length();

            var square = 0.f;
            final var vector = new float[dimensions];
            for (int i = 0; i < candidate.length(); i++) {
                final var element = candidate.floatValue(i);
                if (!Float.isFinite(element)) {
                    return null;
                }
                square += element * element;
                vector[i] = element;
            }

            if (square <= 0.f || !Float.isFinite(square)) {
                return null;
            }

            return vector;
        }

        @Override
        public float[] maybeToValidVector(List<Double> candidate) {
            if (candidate == null || candidate.isEmpty()) {
                return null;
            }

            final var dimensions = candidate.size();

            var square = 0.f;
            final var vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                final var rawElement = candidate.get(i);
                final float element;
                if (rawElement == null || !Float.isFinite(element = rawElement.floatValue())) {
                    return null;
                }
                square += element * element;
                vector[i] = element;
            }

            if (square <= 0.f || !Float.isFinite(square)) {
                return null;
            }

            return vector;
        }
    };

    public static final EnumSet<VectorSimilarityFunction> SUPPORTED = EnumSet.allOf(VectorSimilarityFunction.class);

    public static VectorSimilarityFunction fromName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            final var exception = new IllegalArgumentException(
                    "'%s' is an unsupported vector similarity function. Supported: %s".formatted(name, SUPPORTED));
            exception.addSuppressed(e);
            throw exception;
        }
    }

    public final float[] maybeToValidVector(Value candidate) {
        if (!(candidate instanceof final FloatingPointArray array)) {
            return null;
        }
        return maybeToValidVector(array);
    }

    public abstract float[] maybeToValidVector(FloatingPointArray candidate);

    public abstract float[] maybeToValidVector(List<Double> candidate);

    public float compare(float[] vector1, float[] vector2) {
        return toLucene().compare(vector1, vector2);
    }

    final org.apache.lucene.index.VectorSimilarityFunction toLucene() {
        return switch (this) {
            case EUCLIDEAN -> org.apache.lucene.index.VectorSimilarityFunction.EUCLIDEAN;
            case COSINE -> org.apache.lucene.index.VectorSimilarityFunction.COSINE;
        };
    }
}
