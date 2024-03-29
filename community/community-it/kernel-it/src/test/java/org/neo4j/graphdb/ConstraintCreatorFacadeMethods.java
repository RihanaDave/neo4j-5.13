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
package org.neo4j.graphdb;

import java.util.function.Consumer;
import org.neo4j.graphdb.schema.ConstraintCreator;

public enum ConstraintCreatorFacadeMethods implements Consumer<ConstraintCreator> {
    UNIQUE(new FacadeMethod<>(
            "ConstraintCreator assertPropertyIsUnique()", self -> self.assertPropertyIsUnique("property"))),
    CREATE(new FacadeMethod<>("ConstraintDefinition create()", ConstraintCreator::create));

    private final FacadeMethod<ConstraintCreator> facadeMethod;

    ConstraintCreatorFacadeMethods(FacadeMethod<ConstraintCreator> facadeMethod) {
        this.facadeMethod = facadeMethod;
    }

    @Override
    public void accept(ConstraintCreator constraintCreator) {
        facadeMethod.accept(constraintCreator);
    }

    @Override
    public String toString() {
        return facadeMethod.toString();
    }
}
