/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.walking;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.executor.ProcedureExecutor;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class CollapsePathMutateProc extends BaseProc {

    static final String DESCRIPTION = "Collapse Path algorithm is a traversal algorithm capable of creating relationships between the start and end nodes of a traversal";

    @Procedure(name = "gds.collapsePath.mutate", mode = READ)
    @Description(DESCRIPTION)
    public Stream<MutateResult> mutate(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return new ProcedureExecutor<>(
            new CollapsePathMutateSpec(),
            executionContext()
        ).compute(graphName, configuration);
    }

    @Procedure(name = "gds.beta.collapsePath.mutate", mode = READ, deprecatedBy = "gds.collapsePath.mutate")
    @Description(DESCRIPTION)
    @Deprecated(forRemoval = true)
    @Internal
    public Stream<MutateResult> betaMutate(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        executionContext()
            .metricsFacade()
            .deprecatedProcedures().called("gds.beta.collapsePath.mutate");

        executionContext()
            .log()
            .warn("Procedure `gds.beta.collapsePath.mutate` has been deprecated, please use `gds.collapsePath.mutate`.");

        return mutate(graphName, configuration);
    }


}
