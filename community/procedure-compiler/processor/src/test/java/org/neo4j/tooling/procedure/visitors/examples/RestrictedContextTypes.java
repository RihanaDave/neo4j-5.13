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
package org.neo4j.tooling.procedure.visitors.examples;

import org.neo4j.common.DependencyResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.InternalLog;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.TerminationGuard;

public class RestrictedContextTypes {

    // BELOW ARE TYPES ALLOWED FOR ANY PROCEDURE|FUNCTION

    @Context
    public GraphDatabaseService graphDatabaseService;

    @Context
    public InternalLog log;

    @Context
    public TerminationGuard terminationGuard;

    @Context
    public SecurityContext securityContext;

    @Context
    public Transaction transaction;

    // BELOW ARE RESTRICTED TYPES, THESE ARE UNSUPPORTED AND SUBJECT TO CHANGE

    @Context
    public GraphDatabaseAPI graphDatabaseAPI;

    @Context
    public KernelTransaction kernelTransaction;

    @Context
    public DependencyResolver dependencyResolver;
}
