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
package org.neo4j.gds.applications.algorithms.pathfinding;

import org.neo4j.gds.api.DatabaseId;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.ImmutableExportedRelationship;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.applications.algorithms.machinery.MutateOrWriteStep;
import org.neo4j.gds.applications.algorithms.machinery.RequestScopedDependencies;
import org.neo4j.gds.core.utils.progress.tasks.TaskProgressTracker;
import org.neo4j.gds.core.write.RelationshipStreamExporter;
import org.neo4j.gds.logging.Log;
import org.neo4j.gds.paths.PathResult;
import org.neo4j.gds.paths.bellmanford.BellmanFordResult;
import org.neo4j.gds.paths.bellmanford.BellmanFordWriteConfig;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

import java.util.List;

import static org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.COSTS_KEY;
import static org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.NODE_IDS_KEY;
import static org.neo4j.gds.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.TOTAL_COST_KEY;

class BellmanFordWriteStep implements MutateOrWriteStep<BellmanFordResult, RelationshipsWritten> {
    private final Log log;
    private final RequestScopedDependencies requestScopedDependencies;
    private final BellmanFordWriteConfig configuration;

    BellmanFordWriteStep(
        Log log,
        RequestScopedDependencies requestScopedDependencies,
        BellmanFordWriteConfig configuration
    ) {
        this.log = log;
        this.requestScopedDependencies = requestScopedDependencies;
        this.configuration = configuration;
    }

    @Override
    public RelationshipsWritten execute(
        Graph graph,
        GraphStore graphStore,
        BellmanFordResult result
    ) {
        var writeRelationshipType = configuration.writeRelationshipType();

        var writeNodeIds = configuration.writeNodeIds();
        var writeCosts = configuration.writeCosts();

        var paths = result.shortestPaths();
        if (configuration.writeNegativeCycles() && result.containsNegativeCycle()) {
            paths = result.negativeCycles();
        }

        var relationshipStream = paths.mapPaths(
            pathResult -> ImmutableExportedRelationship.of(
                pathResult.sourceNode(),
                pathResult.targetNode(),
                createValues(graph, pathResult, writeNodeIds, writeCosts)
            )
        );

        var progressTracker = new TaskProgressTracker(
            RelationshipStreamExporter.baseTask("Write shortest Paths"),
            (org.neo4j.logging.Log) log.getNeo4jLog(),
            1,
            requestScopedDependencies.getTaskRegistryFactory()
        );

        var exporter = requestScopedDependencies.getRelationshipStreamExporterBuilder()
            .withIdMappingOperator(graph::toOriginalNodeId)
            .withRelationships(relationshipStream)
            .withTerminationFlag(requestScopedDependencies.getTerminationFlag())
            .withProgressTracker(progressTracker)
            .withArrowConnectionInfo(
                configuration.arrowConnectionInfo(),
                graphStore.databaseInfo().remoteDatabaseId().map(DatabaseId::databaseName)
            )
            .withResultStore(configuration.resolveResultStore(graphStore.resultStore()))
            .build();

        // effect
        var relationshipsWritten = exporter.write(
            writeRelationshipType,
            createKeys(writeNodeIds, writeCosts),
            createTypes(writeNodeIds, writeCosts)
        );

        // reporting
        return new RelationshipsWritten(relationshipsWritten);
    }

    private Value[] createValues(IdMap idMap, PathResult pathResult, boolean writeNodeIds, boolean writeCosts) {
        if (writeNodeIds && writeCosts) {
            return new Value[]{
                Values.doubleValue(pathResult.totalCost()),
                Values.longArray(toOriginalIds(idMap, pathResult.nodeIds())),
                Values.doubleArray(pathResult.costs())
            };
        }
        if (writeNodeIds) {
            return new Value[]{
                Values.doubleValue(pathResult.totalCost()),
                Values.longArray(toOriginalIds(idMap, pathResult.nodeIds())),
            };
        }
        if (writeCosts) {
            return new Value[]{
                Values.doubleValue(pathResult.totalCost()),
                Values.doubleArray(pathResult.costs())
            };
        }
        return new Value[]{
            Values.doubleValue(pathResult.totalCost()),
        };
    }

    private long[] toOriginalIds(IdMap idMap, long[] internalIds) {
        for (int i = 0; i < internalIds.length; i++) {
            internalIds[i] = idMap.toOriginalNodeId(internalIds[i]);
        }
        return internalIds;
    }

    private List<String> createKeys(boolean writeNodeIds, boolean writeCosts) {
        if (writeNodeIds && writeCosts) {
            return List.of(
                TOTAL_COST_KEY,
                NODE_IDS_KEY,
                COSTS_KEY
            );
        }
        if (writeNodeIds) {
            return List.of(
                TOTAL_COST_KEY,
                NODE_IDS_KEY
            );
        }
        if (writeCosts) {
            return List.of(
                TOTAL_COST_KEY,
                COSTS_KEY
            );
        }
        return List.of(TOTAL_COST_KEY);
    }

    private List<ValueType> createTypes(boolean writeNodeIds, boolean writeCosts) {
        if (writeNodeIds && writeCosts) {
            return List.of(
                ValueType.DOUBLE,
                ValueType.LONG_ARRAY,
                ValueType.DOUBLE_ARRAY
            );
        }
        if (writeNodeIds) {
            return List.of(
                ValueType.DOUBLE,
                ValueType.LONG_ARRAY
            );
        }
        if (writeCosts) {
            return List.of(
                ValueType.DOUBLE,
                ValueType.DOUBLE_ARRAY
            );
        }
        return List.of(ValueType.DOUBLE);
    }
}
