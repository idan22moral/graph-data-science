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
package org.neo4j.gds.procedures.community.leiden;

import org.neo4j.gds.api.ProcedureReturnColumns;
import org.neo4j.gds.result.AbstractCommunityResultBuilder;
import org.neo4j.gds.procedures.algorithms.results.StandardStatsResult;

import java.util.List;
import java.util.Map;

public class LeidenStatsResult extends StandardStatsResult {
    public final long ranLevels;
    public final boolean didConverge;
    public final long nodeCount;
    public final long communityCount;
    public final Map<String, Object> communityDistribution;
    public final double modularity;
    public final List<Double> modularities;

    public LeidenStatsResult(
        long ranLevels,
        boolean didConverge,
        long nodeCount,
        long communityCount,
        Map<String, Object> communityDistribution,
        double modularity,
        List<Double> modularities,
        long preProcessingMillis,
        long computeMillis,
        long postProcessingMillis,
        Map<String, Object> configuration
    ) {
        super(preProcessingMillis, computeMillis, postProcessingMillis, configuration);
        this.ranLevels = ranLevels;
        this.didConverge = didConverge;
        this.nodeCount = nodeCount;
        this.communityCount = communityCount;
        this.communityDistribution = communityDistribution;
        this.modularities = modularities;
        this.modularity = modularity;
    }

    public static class StatsBuilder extends AbstractCommunityResultBuilder<LeidenStatsResult> {

        long levels = -1;
        boolean didConverge = false;
        double modularity;
        List<Double> modularities;

        public StatsBuilder(ProcedureReturnColumns returnColumns, int concurrency) {
            super(returnColumns, concurrency);
        }

        public StatsBuilder withLevels(long levels) {
            this.levels = levels;
            return this;
        }

        public StatsBuilder withModularity(double modularity) {
            this.modularity = modularity;
            return this;
        }

        public StatsBuilder withModularities(List<Double> modularities) {
            this.modularities = modularities;
            return this;
        }

        public StatsBuilder withDidConverge(boolean didConverge) {
            this.didConverge = didConverge;
            return this;
        }

        @Override
        protected LeidenStatsResult buildResult() {
            return new LeidenStatsResult(
                levels,
                didConverge,
                nodeCount,
                maybeCommunityCount.orElse(0L),
                communityHistogramOrNull(),
                modularity,
                modularities,
                preProcessingMillis,
                computeMillis,
                postProcessingDuration,
                config.toMap()
            );
        }
    }

}
