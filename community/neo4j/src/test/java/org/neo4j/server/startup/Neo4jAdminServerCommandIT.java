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
package org.neo4j.server.startup;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;

/**
 * A test for some commands in 'neo4j-admin server' group.
 */
@Isolated
public class Neo4jAdminServerCommandIT extends ServerCommandIT {

    @Override
    protected CommandLine createCommand(
            PrintStream out,
            PrintStream err,
            Function<String, String> envLookup,
            Function<String, String> propLookup,
            Runtime.Version version) {
        var environment = new Environment(out, err, envLookup, propLookup, version);
        return Neo4jCommand.asCommandLine(new Neo4jAdminCommand(environment), environment);
    }

    @Override
    protected int execute(List<String> args, Map<String, String> env, Runtime.Version version) {
        List<String> newArgs = new ArrayList<>();
        newArgs.add("server");
        newArgs.addAll(args);

        return super.execute(newArgs, env, version);
    }
}
