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
package org.neo4j.cypher.internal.util

/**
 * Describes a notification
 */
trait InternalNotification

case class CartesianProductNotification(position: InputPosition, isolatedVariables: Set[String], pattern: String)
    extends InternalNotification

case class UnboundedShortestPathNotification(position: InputPosition, pattern: String) extends InternalNotification

case class DeprecatedFunctionNotification(position: InputPosition, oldName: String, newName: String)
    extends InternalNotification

case class DeprecatedRelTypeSeparatorNotification(
  position: InputPosition,
  oldExpression: String,
  rewrittenExpression: String
) extends InternalNotification

case class DeprecatedNodesOrRelationshipsInSetClauseNotification(
  position: InputPosition,
  deprecated: String,
  replacement: String
) extends InternalNotification

case class DeprecatedPropertyReferenceInCreate(position: InputPosition, varName: String) extends InternalNotification

case class SubqueryVariableShadowing(position: InputPosition, varName: String) extends InternalNotification

case class UnionReturnItemsInDifferentOrder(position: InputPosition) extends InternalNotification

case class HomeDatabaseNotPresent(databaseName: String) extends InternalNotification

case class FixedLengthRelationshipInShortestPath(position: InputPosition, deprecated: String, replacement: String)
    extends InternalNotification

case class DeprecatedDatabaseNameNotification(databaseName: String, position: Option[InputPosition])
    extends InternalNotification

case class DeprecatedRuntimeNotification(msg: String, oldOption: String, newOption: String)
    extends InternalNotification

case class DeprecatedTextIndexProvider(position: InputPosition) extends InternalNotification

case class UnsatisfiableRelationshipTypeExpression(position: InputPosition, labelExpression: String)
    extends InternalNotification

case class RepeatedRelationshipReference(position: InputPosition, relName: String, pattern: String)
    extends InternalNotification

case class RepeatedVarLengthRelationshipReference(position: InputPosition, relName: String) extends InternalNotification

case class DeprecatedConnectComponentsPlannerPreParserOption(position: InputPosition) extends InternalNotification

case class AssignPrivilegeCommandHasNoEffectNotification(command: String) extends InternalNotification
case class RevokePrivilegeCommandHasNoEffectNotification(command: String) extends InternalNotification
case class GrantRoleCommandHasNoEffectNotification(command: String) extends InternalNotification
case class RevokeRoleCommandHasNoEffectNotification(command: String) extends InternalNotification

case class ImpossibleRevokeCommandWarning(command: String, cause: String) extends InternalNotification

case class ServerAlreadyEnabled(server: String) extends InternalNotification
case class ServerAlreadyCordoned(server: String) extends InternalNotification
