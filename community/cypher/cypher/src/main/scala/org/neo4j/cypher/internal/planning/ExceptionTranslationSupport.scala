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
package org.neo4j.cypher.internal.planning

import org.neo4j.common.TokenNameLookup
import org.neo4j.cypher.internal.planner.spi.ReadTokenContext

/**
 * Intended to be used together with [[org.neo4j.cypher.internal.macros.TranslateExceptionMacros]]
 */
trait ExceptionTranslationSupport {
  inner: ReadTokenContext =>

  val tokenNameLookup: TokenNameLookup = new TokenNameLookup {
    def propertyKeyGetName(propertyKeyId: Int): String = inner.getPropertyKeyName(propertyKeyId)

    def labelGetName(labelId: Int): String = inner.getLabelName(labelId)

    def relationshipTypeGetName(relTypeId: Int): String = inner.getRelTypeName(relTypeId)
  }
}
