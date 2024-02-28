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
package org.neo4j.tooling.procedure.messages;

import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;

public class ContextFieldError implements CompilationMessage {
    private final Element element;
    private final String contents;

    public ContextFieldError(VariableElement element, String message, Object... args) {
        this.element = element;
        this.contents = String.format(message, args);
    }

    @Override
    public Element getElement() {
        return element;
    }

    @Override
    public String getContents() {
        return contents;
    }
}