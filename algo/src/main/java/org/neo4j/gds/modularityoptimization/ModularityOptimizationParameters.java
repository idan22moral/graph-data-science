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
package org.neo4j.gds.modularityoptimization;

public final class ModularityOptimizationParameters {

    public static ModularityOptimizationParameters create(
        int concurrency,
        int maxIterations,
        int batchSize,
        double tolerance
    ) {
        return new ModularityOptimizationParameters(concurrency, maxIterations, batchSize, tolerance);
    }

    private final int concurrency;
    private final int maxIterations;
    private final int batchSize;
    private final double tolerance;

    private ModularityOptimizationParameters(int concurrency, int maxIterations, int batchSize, double tolerance) {
        this.concurrency = concurrency;
        this.maxIterations = maxIterations;
        this.batchSize = batchSize;
        this.tolerance = tolerance;
    }

    int concurrency() {
        return concurrency;
    }

    int maxIterations() {
        return maxIterations;
    }

    int batchSize() {
        return batchSize;
    }

    double tolerance() {
        return tolerance;
    }
}
