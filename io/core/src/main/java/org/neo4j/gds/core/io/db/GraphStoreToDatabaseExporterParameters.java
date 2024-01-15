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
package org.neo4j.gds.core.io.db;

import org.neo4j.gds.PropertyMappings;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.internal.batchimport.Configuration;
import org.neo4j.internal.batchimport.IndexConfig;

import java.util.Optional;

public final class GraphStoreToDatabaseExporterParameters {
    private final String dbName;
    private final String recordFormat;
    private final PropertyMappings additionalNodeProperties;
    private final String defaultRelationshipType;
    private final int writeConcurrency;
    private final int batchSize;
    private final boolean useBadCollector;
    private final boolean highIO;
    private final long executionMonitorCheckMillis;
    private final boolean enableDebugLog;
    private boolean force;
    private Optional<Long> pageCacheMemory;

    static GraphStoreToDatabaseExporterParameters create(
        String dbName,
        String recordFormat,
        PropertyMappings additionalNodeProperties,
        String defaultRelationshipType,
        int writeConcurrency,
        int batchSize,
        boolean useBadCollector,
        boolean highIO,
        long executionMonitorCheckMillis,
        boolean enableDebugLog
    ) {
        return new GraphStoreToDatabaseExporterParameters(
            dbName,
            recordFormat,
            additionalNodeProperties,
            defaultRelationshipType,
            writeConcurrency,
            batchSize,
            useBadCollector,
            highIO,
            executionMonitorCheckMillis,
            enableDebugLog
        );
    }

    private GraphStoreToDatabaseExporterParameters(
        String dbName,
        String recordFormat,
        PropertyMappings additionalNodeProperties,
        String defaultRelationshipType,
        int writeConcurrency,
        int batchSize,
        boolean useBadCollector,
        boolean highIO,
        long executionMonitorCheckMillis,
        boolean enableDebugLog
    ) {
        this.dbName = dbName;
        this.recordFormat = recordFormat;
        this.additionalNodeProperties = additionalNodeProperties;
        this.defaultRelationshipType = defaultRelationshipType;
        this.writeConcurrency = writeConcurrency;
        this.batchSize = batchSize;
        this.useBadCollector = useBadCollector;
        this.highIO = highIO;
        this.executionMonitorCheckMillis = executionMonitorCheckMillis;
        this.enableDebugLog = enableDebugLog;

        this.force = false;
        this.pageCacheMemory = Optional.empty();
    }

    String dbName() {
        return dbName;
    }

    String recordFormat() {
        return recordFormat;
    }

    boolean useBadCollector() {
        return useBadCollector;
    }

    boolean force() {
        return force;
    }

    boolean enableDebugLog() {
        return enableDebugLog;
    }

    public GraphStoreToDatabaseExporterParameters withPageCacheMemory(long pageCacheMemory) {
        this.pageCacheMemory = Optional.of(pageCacheMemory);
        return this;
    }

    public GraphStoreToDatabaseExporterParameters withForce(boolean force) {
        this.force = force;
        return this;
    }

    Configuration toBatchImporterConfig() {
        return Neo4jProxy.batchImporterConfig(
            batchSize,
            writeConcurrency,
            pageCacheMemory,
            highIO,
            IndexConfig.DEFAULT.withLabelIndex().withRelationshipTypeIndex()
        );
    }
}
