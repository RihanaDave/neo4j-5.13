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
package org.neo4j.storageengine.api;

public interface ReadTracer {
    /**
     * Called when reading a node.
     *
     * @param nodeReference the node reference that will be available.
     */
    void onNode(long nodeReference);

    /**
     * Called on all-node scan.
     */
    void onAllNodesScan();

    /**
     * Called when traversing a relationship.
     *
     * @param relationshipReference the relationship reference that will be available.
     */
    void onRelationship(long relationshipReference);

    /**
     * Called when reading a property.
     *
     * @param propertyKey the property key of the next property.
     */
    void onProperty(int propertyKey);

    /**
     * Called when checking for existence of a label.
     */
    void onHasLabel(int label);

    /**
     * Called when checking for existence of any label.
     */
    void onHasLabel();

    void dbHit();
}
