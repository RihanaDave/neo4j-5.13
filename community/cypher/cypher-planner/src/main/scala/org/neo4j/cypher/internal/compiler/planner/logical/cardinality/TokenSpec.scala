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
package org.neo4j.cypher.internal.compiler.planner.logical.cardinality

import org.neo4j.cypher.internal.util.LabelId
import org.neo4j.cypher.internal.util.NameId
import org.neo4j.cypher.internal.util.RelTypeId
import org.neo4j.exceptions.InternalException

sealed trait TokenSpec[+ID <: NameId] {
  def id: Option[ID]

  def map[T](f: Option[ID] => T): Option[T]
}

object TokenSpec {
  type LabelSpecs = Map[String, Set[TokenSpec[LabelId]]]
  type RelTypeSpecs = Map[String, Set[TokenSpec[RelTypeId]]]
}

case class SpecifiedButUnknown() extends TokenSpec[Nothing] {
  def id = throw new InternalException("Tried to use a token id unknown to the schema")

  override def map[T](f: Option[Nothing] => T): Option[T] = None
}

case object Unspecified extends TokenSpec[Nothing] {
  def id = None

  override def map[T](f: Option[Nothing] => T): Option[T] = Some(f(None))
}

case class SpecifiedAndKnown[+ID <: NameId](_id: ID) extends TokenSpec[ID] {
  def id = Some(_id)

  override def map[T](f: Option[ID] => T): Option[T] = Some(f(id))
}
