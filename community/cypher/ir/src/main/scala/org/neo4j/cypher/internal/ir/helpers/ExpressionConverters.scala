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
package org.neo4j.cypher.internal.ir.helpers

import org.neo4j.cypher.internal.expressions.And
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasLabels
import org.neo4j.cypher.internal.expressions.HasTypes
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.ir.Predicate
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren

import scala.collection.immutable.ListSet

object ExpressionConverters {

  implicit class PredicateConverter(val predicate: Expression) extends AnyVal {

    def asPredicates: ListSet[Predicate] = {
      asPredicates(ListSet.empty)
    }

    def asPredicates(outerScope: Set[String]): ListSet[Predicate] = {
      predicate.folder.treeFold(ListSet.empty[Predicate]) {
        // n:Label
        case p @ HasLabels(Variable(name), labels) =>
          acc =>
            val newAcc = acc ++ labels.map { label =>
              Predicate(Set(name), p.copy(labels = Seq(label))(p.position))
            }
            SkipChildren(newAcc)
        // r:T
        case p @ HasTypes(Variable(name), types) =>
          acc =>
            val newAcc = acc ++ types.map { typ =>
              Predicate(Set(name), p.copy(types = Seq(typ))(p.position))
            }
            SkipChildren(newAcc)
        // and
        case _: Ands | _: And =>
          acc => TraverseChildren(acc)
        case p: Expression =>
          acc => SkipChildren(acc + Predicate(p.idNames -- outerScope, p))
      }
    }
  }

  implicit class IdExtractor(val exp: Expression) extends AnyVal {
    def idNames: Set[String] = exp.dependencies.map(id => id.name)
  }

}
