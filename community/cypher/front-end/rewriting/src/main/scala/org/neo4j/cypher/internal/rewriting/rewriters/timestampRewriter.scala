/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.rewriting.rewriters

import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.rewriting.conditions.FunctionInvocationsResolved
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.rewriting.rewriters.factories.PreparatoryRewritingRewriterFactory
import org.neo4j.cypher.internal.util.CypherExceptionFactory
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.Condition
import org.neo4j.cypher.internal.util.StepSequencer.Step
import org.neo4j.cypher.internal.util.topDown

case object TimestampRewritten extends Condition

case object timestampRewriter extends Step with PreparatoryRewritingRewriterFactory {

  override def getRewriter(cypherExceptionFactory: CypherExceptionFactory): Rewriter = instance

  override def preConditions: Set[StepSequencer.Condition] = Set(!FunctionInvocationsResolved)

  override def postConditions: Set[StepSequencer.Condition] = Set(TimestampRewritten)

  override def invalidatedConditions: Set[StepSequencer.Condition] = SemanticInfoAvailable

  private val rewriter = Rewriter.lift {

    case f @ FunctionInvocation(namespace, FunctionName(name), _, _)
      if namespace.parts.isEmpty && name.equalsIgnoreCase("timestamp") =>
      val datetimeFunction = f.copy(functionName = FunctionName("datetime")(f.functionName.position))(f.position)
      Property(datetimeFunction, PropertyKeyName("epochMillis")(datetimeFunction.position))(datetimeFunction.position)
  }

  val instance: Rewriter = topDown(rewriter)
}
