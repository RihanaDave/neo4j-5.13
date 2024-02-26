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
package org.neo4j.cypher.internal.ast.factory.neo4j

import org.neo4j.cypher.internal.cst.factory.neo4j.AntlrRule
import org.neo4j.cypher.internal.cst.factory.neo4j.Cst
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.label_expressions.LabelExpressionPredicate

/**
 * The aim of this class is to test parsing for all combinations of
 * IS and WHERE used in relationship patterns e.g. [WHERE IS WHERE WHERE IS]
 */
class IsWhereRelationshipPatternParserTest
    extends ParserSyntaxTreeBase[Cst.RelationshipPattern, RelationshipPattern] {

  implicit val javaccRule: JavaccRule[RelationshipPattern] = JavaccRule.RelationshipPattern
  implicit val antlrRule: AntlrRule[Cst.RelationshipPattern] = AntlrRule.RelationshipPattern

  for {
    (maybeVariable, maybeVariableName) <-
      Seq(("", None), ("IS", Some("IS")), ("WHERE", Some("WHERE")))
  } yield {
    test(s"-[$maybeVariable]->") {
      gives(relPat(maybeVariableName))
    }

    for {
      isOrWhere <- Seq("IS", "WHERE")
    } yield {
      test(s"-[$maybeVariable IS $isOrWhere]->") {
        gives(
          relPat(
            maybeVariableName,
            labelExpression = Some(labelRelTypeLeaf(isOrWhere, containsIs = true))
          )
        )
      }

      test(s"-[$maybeVariable WHERE $isOrWhere]->") {
        gives(
          relPat(
            maybeVariableName,
            predicates = Some(varFor(isOrWhere))
          )
        )
      }

      for {
        isOrWhere2 <- Seq("IS", "WHERE")
      } yield {
        test(s"-[$maybeVariable IS $isOrWhere WHERE $isOrWhere2]->") {
          gives(
            relPat(
              maybeVariableName,
              labelExpression = Some(labelRelTypeLeaf(isOrWhere, containsIs = true)),
              predicates = Some(varFor(isOrWhere2))
            )
          )
        }

        test(s"-[$maybeVariable WHERE $isOrWhere IS $isOrWhere2]->") {
          gives(
            relPat(
              maybeVariableName,
              predicates = Some(LabelExpressionPredicate(
                varFor(isOrWhere),
                labelOrRelTypeLeaf(isOrWhere2, containsIs = true)
              )(pos))
            )
          )
        }

        test(s"-[$maybeVariable WHERE $isOrWhere WHERE $isOrWhere2]->") {
          failsToParse
        }

        test(s"-[$maybeVariable IS $isOrWhere IS $isOrWhere2]->") {
          failsToParse
        }

        for {
          isOrWhere3 <- Seq("IS", "WHERE")
        } yield {
          test(s"-[$maybeVariable IS $isOrWhere WHERE $isOrWhere2 IS $isOrWhere3]->") {
            gives(
              relPat(
                maybeVariableName,
                labelExpression = Some(labelRelTypeLeaf(isOrWhere, containsIs = true)),
                predicates = Some(LabelExpressionPredicate(
                  varFor(isOrWhere2),
                  labelOrRelTypeLeaf(isOrWhere3, containsIs = true)
                )(pos))
              )
            )
          }
        }
      }
    }
  }
}
