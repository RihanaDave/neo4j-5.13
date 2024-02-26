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
package org.neo4j.cypher.internal.runtime.interpreted.commands.showcommands

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.neo4j.cypher.internal.ast.CurrentUser
import org.neo4j.cypher.internal.ast.ShowProceduresClause
import org.neo4j.cypher.internal.ast.User
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.graphdb.Result
import org.neo4j.internal.kernel.api.procs.FieldSignature
import org.neo4j.internal.kernel.api.procs.Neo4jTypes.NTBoolean
import org.neo4j.internal.kernel.api.procs.Neo4jTypes.NTInteger
import org.neo4j.internal.kernel.api.procs.Neo4jTypes.NTString
import org.neo4j.internal.kernel.api.procs.ProcedureSignature
import org.neo4j.internal.kernel.api.procs.QualifiedName
import org.neo4j.internal.kernel.api.security.AdminActionOnResource
import org.neo4j.internal.kernel.api.security.AuthSubject
import org.neo4j.internal.kernel.api.security.PermissionState
import org.neo4j.internal.kernel.api.security.PrivilegeAction.SHOW_ROLE
import org.neo4j.internal.kernel.api.security.Segment
import org.neo4j.procedure.Mode
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.Values
import org.neo4j.values.virtual.VirtualValues

import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.jdk.CollectionConverters.SetHasAsJava

class ShowProceduresCommandTest extends ShowCommandTestBase {

  private val defaultColumns =
    ShowProceduresClause(None, None, hasYield = false)(InputPosition.NONE)
      .unfilteredColumns
      .columns

  private val allColumns =
    ShowProceduresClause(None, None, hasYield = true)(InputPosition.NONE)
      .unfilteredColumns
      .columns

  private val proc1 = new ProcedureSignature(
    new QualifiedName(List.empty[String].asJava, "proc1"),
    List.empty[FieldSignature].asJava,
    List.empty[FieldSignature].asJava,
    Mode.READ,
    false,
    null,
    "Non-admin, non-system, void read procedure without input parameters",
    null,
    false,
    true,
    false,
    false,
    false,
    false
  )

  private val proc2 = new ProcedureSignature(
    new QualifiedName(List.empty[String].asJava, "proc2"),
    List(FieldSignature.inputField("input", NTString)).asJava,
    List(
      FieldSignature.outputField("booleanOutput", NTBoolean),
      FieldSignature.outputField("intOutput", NTInteger)
    ).asJava,
    Mode.WRITE,
    true,
    null,
    "Admin, non-system, write procedure",
    null,
    false,
    true,
    false,
    false,
    false,
    false
  )

  private val proc3 = new ProcedureSignature(
    new QualifiedName(List("zzz").asJava, "proc3"),
    List.empty[FieldSignature].asJava,
    List(FieldSignature.outputField("output", NTString)).asJava,
    Mode.DBMS,
    false,
    null,
    "Non-admin, system, dbms procedure",
    null,
    false,
    true,
    true,
    false,
    false,
    false
  )

  override def beforeEach(): Unit = {
    super.beforeEach()

    // Defaults:
    mockSetupProcFunc()
    when(systemTx.execute(any())).thenAnswer(invocation => handleSystemQueries(invocation.getArgument(0)))
    when(systemTx.execute(any(), any())).thenAnswer(invocation => handleSystemQueries(invocation.getArgument(0)))
  }

  private def handleSystemQueries(query: String): Result = handleSystemQueries(query, "PROCEDURE")

  // Only checks the given parameters
  private def checkResult(
    resultMap: Map[String, AnyValue],
    name: Option[String] = None,
    description: Option[String] = None,
    mode: Option[String] = None,
    worksOnSystem: Option[Boolean] = None,
    signature: Option[String] = None,
    argumentDescription: Option[List[AnyValue]] = None,
    returnDescription: Option[List[AnyValue]] = None,
    admin: Option[Boolean] = None,
    roles: Option[List[String]] = None,
    rolesBoosted: Option[List[String]] = None,
    isDeprecated: Option[Boolean] = None,
    option: Option[Map[String, AnyValue]] = None
  ): Unit = {
    name.foreach(expected => resultMap("name") should be(Values.stringValue(expected)))
    description.foreach(expected => resultMap("description") should be(Values.stringValue(expected)))
    mode.foreach(expected => resultMap("mode") should be(Values.stringValue(expected)))
    worksOnSystem.foreach(expected => resultMap("worksOnSystem") should be(Values.booleanValue(expected)))
    signature.foreach(expected => resultMap("signature") should be(Values.stringValue(expected)))
    argumentDescription.foreach(expected =>
      resultMap("argumentDescription") should be(VirtualValues.list(expected: _*))
    )
    returnDescription.foreach(expected =>
      resultMap("returnDescription") should be(VirtualValues.list(expected: _*))
    )
    admin.foreach(expected => resultMap("admin") should be(Values.booleanValue(expected)))
    roles.foreach(expected =>
      if (expected == null) resultMap("rolesExecution") should be(Values.NO_VALUE)
      else resultMap("rolesExecution") should be(VirtualValues.list(expected.map(Values.stringValue): _*))
    )
    rolesBoosted.foreach(expected =>
      if (expected == null) resultMap("rolesBoostedExecution") should be(Values.NO_VALUE)
      else resultMap("rolesBoostedExecution") should be(VirtualValues.list(expected.map(Values.stringValue): _*))
    )
    isDeprecated.foreach(expected => resultMap("isDeprecated") should be(Values.booleanValue(expected)))
    option.foreach(expected =>
      resultMap("option") should be(VirtualValues.map(expected.view.keys.toArray, expected.view.values.toArray))
    )
  }

  // Tests

  test("show procedures should give back correct community default values") {
    // Set-up which procedures to return:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, proc2, proc3).asJava)

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = false, defaultColumns, isCommunity = true)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 3
    checkResult(
      result.head,
      name = "proc1",
      description = "Non-admin, non-system, void read procedure without input parameters",
      mode = Mode.READ.name(),
      worksOnSystem = false
    )
    checkResult(
      result(1),
      name = "proc2",
      description = "Admin, non-system, write procedure",
      mode = Mode.WRITE.name(),
      worksOnSystem = false
    )
    checkResult(
      result(2),
      name = "zzz.proc3",
      description = "Non-admin, system, dbms procedure",
      mode = Mode.DBMS.name(),
      worksOnSystem = true
    )
    // confirm no verbose columns:
    result.foreach(res => {
      res.keys.toList should contain noElementsOf List(
        "signature",
        "argumentDescription",
        "returnDescription",
        "admin",
        "rolesExecution",
        "rolesBoostedExecution",
        "isDeprecated",
        "option"
      )
    })
  }

  test("show procedures should give back correct enterprise default values") {
    // Set-up which procedures to return:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, proc2, proc3).asJava)

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = false, defaultColumns, isCommunity = false)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 3
    checkResult(
      result.head,
      name = "proc1",
      description = "Non-admin, non-system, void read procedure without input parameters",
      mode = Mode.READ.name(),
      worksOnSystem = false
    )
    checkResult(
      result(1),
      name = "proc2",
      description = "Admin, non-system, write procedure",
      mode = Mode.WRITE.name(),
      worksOnSystem = false
    )
    checkResult(
      result(2),
      name = "zzz.proc3",
      description = "Non-admin, system, dbms procedure",
      mode = Mode.DBMS.name(),
      worksOnSystem = true
    )
    // confirm no verbose columns:
    result.foreach(res => {
      res.keys.toList should contain noElementsOf List(
        "signature",
        "argumentDescription",
        "returnDescription",
        "admin",
        "rolesExecution",
        "rolesBoostedExecution",
        "isDeprecated",
        "option"
      )
    })
  }

  test("show procedures should give back correct community full values") {
    // Set-up which procedures to return:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, proc2, proc3).asJava)

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = true, allColumns, isCommunity = true)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 3
    checkResult(
      result.head,
      name = "proc1",
      description = "Non-admin, non-system, void read procedure without input parameters",
      mode = Mode.READ.name(),
      worksOnSystem = false,
      signature = "proc1() :: ()",
      argumentDescription = List.empty[AnyValue],
      returnDescription = List.empty[AnyValue],
      admin = false,
      roles = Some(null),
      rolesBoosted = Some(null),
      isDeprecated = false,
      option = Map("deprecated" -> Values.FALSE)
    )
    checkResult(
      result(1),
      name = "proc2",
      description = "Admin, non-system, write procedure",
      mode = Mode.WRITE.name(),
      worksOnSystem = false,
      signature = "proc2(input :: STRING) :: (booleanOutput :: BOOLEAN, intOutput :: INTEGER)",
      argumentDescription = List(argumentAndReturnDescriptionMaps("input", "input :: STRING", "STRING")),
      returnDescription = List(
        argumentAndReturnDescriptionMaps("booleanOutput", "booleanOutput :: BOOLEAN", "BOOLEAN"),
        argumentAndReturnDescriptionMaps("intOutput", "intOutput :: INTEGER", "INTEGER")
      ),
      admin = true,
      roles = Some(null),
      rolesBoosted = Some(null),
      isDeprecated = false,
      option = Map("deprecated" -> Values.FALSE)
    )
    checkResult(
      result(2),
      name = "zzz.proc3",
      description = "Non-admin, system, dbms procedure",
      mode = Mode.DBMS.name(),
      worksOnSystem = true,
      signature = "zzz.proc3() :: (output :: STRING)",
      argumentDescription = List.empty[AnyValue],
      returnDescription = List(argumentAndReturnDescriptionMaps("output", "output :: STRING", "STRING")),
      admin = false,
      roles = Some(null),
      rolesBoosted = Some(null),
      isDeprecated = false,
      option = Map("deprecated" -> Values.FALSE)
    )
  }

  test("show procedures should give back correct enterprise full values") {
    // Set-up which procedures to return:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, proc2, proc3).asJava)

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = true, allColumns, isCommunity = false)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 3
    checkResult(
      result.head,
      name = "proc1",
      description = "Non-admin, non-system, void read procedure without input parameters",
      mode = Mode.READ.name(),
      worksOnSystem = false,
      signature = "proc1() :: ()",
      argumentDescription = List.empty[AnyValue],
      returnDescription = List.empty[AnyValue],
      admin = false,
      roles = List(publicRole, adminRole),
      rolesBoosted = List(adminRole),
      isDeprecated = false,
      option = Map("deprecated" -> Values.FALSE)
    )
    checkResult(
      result(1),
      name = "proc2",
      description = "Admin, non-system, write procedure",
      mode = Mode.WRITE.name(),
      worksOnSystem = false,
      signature = "proc2(input :: STRING) :: (booleanOutput :: BOOLEAN, intOutput :: INTEGER)",
      argumentDescription = List(argumentAndReturnDescriptionMaps("input", "input :: STRING", "STRING")),
      returnDescription = List(
        argumentAndReturnDescriptionMaps("booleanOutput", "booleanOutput :: BOOLEAN", "BOOLEAN"),
        argumentAndReturnDescriptionMaps("intOutput", "intOutput :: INTEGER", "INTEGER")
      ),
      admin = true,
      roles = List(adminRole),
      rolesBoosted = List(adminRole),
      isDeprecated = false,
      option = Map("deprecated" -> Values.FALSE)
    )
    checkResult(
      result(2),
      name = "zzz.proc3",
      description = "Non-admin, system, dbms procedure",
      mode = Mode.DBMS.name(),
      worksOnSystem = true,
      signature = "zzz.proc3() :: (output :: STRING)",
      argumentDescription = List.empty[AnyValue],
      returnDescription = List(argumentAndReturnDescriptionMaps("output", "output :: STRING", "STRING")),
      admin = false,
      roles = List(publicRole, adminRole),
      rolesBoosted = List(adminRole),
      isDeprecated = false,
      option = Map("deprecated" -> Values.FALSE)
    )
  }

  test("show procedures should return the procedures sorted on name") {
    // Set-up which procedures to return, not ordered by name:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc2, proc3, proc1).asJava)

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = false, defaultColumns, isCommunity = true)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 3
    checkResult(result.head, name = "proc1")
    checkResult(result(1), name = "proc2")
    checkResult(result(2), name = "zzz.proc3")
  }

  test("show procedures should not return internal procedures") {
    // Set-up which procedures to return:
    val internalProc = new ProcedureSignature(
      new QualifiedName(List("internal").asJava, "proc"),
      List.empty[FieldSignature].asJava,
      List.empty[FieldSignature].asJava,
      Mode.READ,
      false,
      null,
      "Internal procedure",
      null,
      false,
      true,
      false,
      true,
      false,
      false
    )
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, internalProc).asJava)

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = false, defaultColumns, isCommunity = true)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 1
    checkResult(result.head, name = "proc1")
  }

  test("show procedures should return deprecated procedures") {
    // Set-up which procedures to return:
    val deprecatedProc = new ProcedureSignature(
      new QualifiedName(List("proc").asJava, "deprecated"),
      List.empty[FieldSignature].asJava,
      List.empty[FieldSignature].asJava,
      Mode.READ,
      false,
      "I'm deprecated",
      "Deprecated procedure",
      null,
      false,
      true,
      false,
      false,
      false,
      false
    )
    when(procedures.proceduresGetAll()).thenReturn(Set(deprecatedProc).asJava)

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = true, allColumns, isCommunity = true)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 1
    checkResult(
      result.head,
      name = "proc.deprecated",
      isDeprecated = true,
      option = Map("deprecated" -> Values.TRUE)
    )
  }

  test("show procedures should not give back roles without SHOW ROLE privilege") {
    // Set-up which procedures to return:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1).asJava)

    // Block SHOW ROLE
    when(securityContext.allowsAdminAction(any())).thenAnswer(_.getArgument[AdminActionOnResource](0) match {
      case a: AdminActionOnResource
        if a.matches(new AdminActionOnResource(SHOW_ROLE, AdminActionOnResource.DatabaseScope.ALL, Segment.ALL)) =>
        PermissionState.NOT_GRANTED
      case _ => PermissionState.EXPLICIT_GRANT
    })

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = true, allColumns, isCommunity = false)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 1
    checkResult(result.head, roles = Some(null), rolesBoosted = Some(null))
  }

  test("show procedures should not give back roles when denied SHOW ROLE privilege") {
    // Set-up which procedures to return:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1).asJava)

    // Block SHOW ROLE
    when(securityContext.allowsAdminAction(any())).thenAnswer(_.getArgument[AdminActionOnResource](0) match {
      case a: AdminActionOnResource
        if a.matches(new AdminActionOnResource(SHOW_ROLE, AdminActionOnResource.DatabaseScope.ALL, Segment.ALL)) =>
        PermissionState.EXPLICIT_DENY
      case _ => PermissionState.EXPLICIT_GRANT
    })

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = true, allColumns, isCommunity = false)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 1
    checkResult(result.head, roles = Some(null), rolesBoosted = Some(null))
  }

  test("show procedures should return correct roles") {
    val boostingRole = "boosted_executor"
    val executeAdminRole = "admin_executor"

    /* proc1:
     *  roles: PUBLIC, admin
     *  boostedRoles:
     *
     * proc2:
     *  roles: admin, admin_executor
     *  boostedRoles: admin, admin_executor
     *
     * proc3:
     *  roles: PUBLIC, admin
     *  boostedRoles: admin, boosted_executor
     */
    def specialHandlingOfPrivileges(query: String): Result = {
      query match {
        case "SHOW ALL PRIVILEGES YIELD * WHERE action='execute' AND segment STARTS WITH $seg RETURN access, segment, collect(role) as roles" =>
          MockResult(List(
            Map("access" -> "GRANTED", "segment" -> "procedure(*)", "roles" -> List(publicRole).asJava),
            Map("access" -> "DENIED", "segment" -> "procedure(*2)", "roles" -> List(publicRole).asJava)
          ))
        case "SHOW ALL PRIVILEGES YIELD * WHERE action STARTS WITH 'execute_boosted' AND segment STARTS WITH $seg RETURN access, segment, collect(role) as roles" =>
          MockResult(List(
            Map("access" -> "GRANTED", "segment" -> "procedure(*3)", "roles" -> List(boostingRole).asJava),
            Map("access" -> "DENIED", "segment" -> "procedure(*1)", "roles" -> List(adminRole).asJava)
          ))
        case "SHOW ALL PRIVILEGES YIELD * WHERE action='execute_admin' RETURN access, collect(role) as roles" =>
          MockResult(List(Map("access" -> "GRANTED", "roles" -> List(executeAdminRole).asJava)))
        case "SHOW ALL PRIVILEGES YIELD * WHERE action IN ['admin', 'dbms_actions'] RETURN access, collect(role) as roles" =>
          MockResult(List(Map("access" -> "GRANTED", "roles" -> List(adminRole).asJava)))
        case _ => handleSystemQueries(query)
      }
    }

    // Set-up which procedures to return:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, proc2, proc3).asJava)

    // Set-up role and privileges
    when(systemTx.execute(any())).thenAnswer(invocation => specialHandlingOfPrivileges(invocation.getArgument(0)))
    when(systemTx.execute(any(), any()))
      .thenAnswer(invocation => specialHandlingOfPrivileges(invocation.getArgument(0)))

    // When
    val showProcedures = ShowProceduresCommand(None, verbose = true, allColumns, isCommunity = false)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 3
    checkResult(
      result.head,
      name = "proc1",
      roles = List(publicRole, adminRole),
      rolesBoosted = List.empty[String]
    )
    checkResult(
      result(1),
      name = "proc2",
      roles = List(adminRole, executeAdminRole),
      rolesBoosted = List(adminRole, executeAdminRole)
    )
    checkResult(
      result(2),
      name = "zzz.proc3",
      roles = List(publicRole, adminRole),
      rolesBoosted = List(adminRole, boostingRole)
    )
  }

  test("show procedures executable by current user should return everything with AUTH_DISABLED") {
    // Set-up which procedures exists:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, proc2).asJava)

    // Set user and privileges
    when(securityContext.subject()).thenReturn(AuthSubject.AUTH_DISABLED)

    when(securityContext.allowsAdminAction(any())).thenReturn(PermissionState.EXPLICIT_DENY)
    when(systemTx.execute(any())).thenReturn(MockResult())
    when(systemTx.execute(any(), any())).thenReturn(MockResult())

    // When
    val showProcedures = ShowProceduresCommand(Some(CurrentUser), verbose = false, defaultColumns, isCommunity = false)
    val result = showProcedures.originalNameRows(queryState, initialCypherRow).toList

    // Then
    result should have size 2
    checkResult(result.head, name = "proc1")
    checkResult(result.last, name = "proc2")
  }

  test("show procedures executable by current user should only return executable procedures") {
    // Set-up which procedures exists:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, proc2).asJava)

    // Set user and privileges
    val username = "my_user"
    val user = mock[AuthSubject]
    when(user.hasUsername(username)).thenReturn(true)
    when(securityContext.subject()).thenReturn(user)
    when(securityContext.roles()).thenReturn(Set(publicRole).asJava)

    // When: EXECUTABLE BY CURRENT USER
    val showProceduresCurrent =
      ShowProceduresCommand(Some(CurrentUser), verbose = false, defaultColumns, isCommunity = false)
    val resultCurrent = showProceduresCurrent.originalNameRows(queryState, initialCypherRow).toList

    // Then
    resultCurrent should have size 1
    checkResult(resultCurrent.head, name = "proc1")

    // When: EXECUTABLE BY <current user>
    val showProceduresSame =
      ShowProceduresCommand(Some(User(username)), verbose = false, defaultColumns, isCommunity = false)
    val resultSame = showProceduresSame.originalNameRows(queryState, initialCypherRow).toList

    // Then
    resultSame should be(resultCurrent)
  }

  test("show procedures executable by given user should only return executable procedures") {
    // Set-up which procedures exists:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1, proc2).asJava)

    // Set user and privileges
    val user = mock[AuthSubject]
    val otherUser = "other_user"
    when(user.hasUsername(otherUser)).thenReturn(false)
    when(securityContext.subject()).thenReturn(user)

    // When
    val showProceduresCurrent =
      ShowProceduresCommand(Some(User(otherUser)), verbose = false, defaultColumns, isCommunity = false)
    val resultCurrent = showProceduresCurrent.originalNameRows(queryState, initialCypherRow).toList

    // Then
    resultCurrent should have size 1
    checkResult(resultCurrent.head, name = "proc1")
  }

  test("show procedures executable by given user should return nothing for non-existing users") {
    // Return no result for missing user
    def specialHandlingOfUserRoles(query: String) = query match {
      case "SHOW USERS YIELD user, roles WHERE user = $name RETURN roles" =>
        MockResult()
      case _ => handleSystemQueries(query)
    }

    // Set-up which procedures exists:
    when(procedures.proceduresGetAll()).thenReturn(Set(proc1).asJava)

    // Set user and privileges
    val user = mock[AuthSubject]
    val missingUser = "missing_user"
    when(user.hasUsername(missingUser)).thenReturn(false)
    when(securityContext.subject()).thenReturn(user)

    when(systemTx.execute(any())).thenAnswer(invocation => specialHandlingOfUserRoles(invocation.getArgument(0)))
    when(systemTx.execute(any(), any()))
      .thenAnswer(invocation => specialHandlingOfUserRoles(invocation.getArgument(0)))

    // When
    val showProceduresCurrent =
      ShowProceduresCommand(Some(User(missingUser)), verbose = false, defaultColumns, isCommunity = false)
    val resultCurrent = showProceduresCurrent.originalNameRows(queryState, initialCypherRow).toList

    // Then
    resultCurrent should have size 0
  }

}
