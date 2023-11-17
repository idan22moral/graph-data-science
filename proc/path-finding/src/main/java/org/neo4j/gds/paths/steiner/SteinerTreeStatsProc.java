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
package org.neo4j.gds.paths.steiner;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.executor.MemoryEstimationExecutor;
import org.neo4j.gds.executor.ProcedureExecutor;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.paths.steiner.Constants.DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

public class SteinerTreeStatsProc extends BaseProc {

    @Procedure(value = "gds.steinerTree.stats", mode = READ)
    @Description(DESCRIPTION)
    public Stream<StatsResult> compute(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return new ProcedureExecutor<>(
            new SteinerTreeStatsSpec(),
            executionContext()
        ).compute(graphName, configuration);
    }

    @Procedure(value = "gds.steinerTree.stats.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphName") Object graphNameOrConfiguration,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return new MemoryEstimationExecutor<>(
            new SteinerTreeStatsSpec(),
            executionContext(),
            transactionContext()
        ).computeEstimate(graphNameOrConfiguration, configuration);
    }

    @Deprecated
    @Procedure(value = "gds.beta.steinerTree.stats", mode = READ, deprecatedBy = "gds.steinerTree.stats")
    @Description(DESCRIPTION)
    @Internal
    public Stream<StatsResult> computeBeta(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        executionContext()
            .metricsFacade()
            .deprecatedProcedures().called("gds.beta.steinerTree.stats");

        executionContext()
            .log()
            .warn("Procedure `gds.beta.steinerTree.stats` has been deprecated, please use `gds.steinerTree.stats`.");
        return compute(graphName, configuration);
    }
}
