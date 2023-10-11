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
package org.neo4j.gds.paths.topologicalsort;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.executor.ProcedureExecutor;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class TopologicalSortStreamProc extends BaseProc {
    static final String TOPOLOGICAL_SORT_DESCRIPTION =
        "Returns all the nodes in the graph that are not part of a cycle or depend on a cycle, sorted in a topological order";

    @Procedure(value = "gds.alpha.topologicalSort.stream", mode = READ)
    @Internal
    @Description(TOPOLOGICAL_SORT_DESCRIPTION)
    public Stream<TopologicalSortStreamResult> stream(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return new ProcedureExecutor<>(
            new TopologicalSortStreamSpec(),
            executionContext()
        ).compute(graphName, configuration);
    }

}