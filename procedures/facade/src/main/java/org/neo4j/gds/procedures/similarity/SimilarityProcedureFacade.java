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
package org.neo4j.gds.procedures.similarity;

import org.neo4j.gds.algorithms.similarity.SimilarityAlgorithmsEstimateBusinessFacade;
import org.neo4j.gds.algorithms.similarity.SimilarityAlgorithmsMutateBusinessFacade;
import org.neo4j.gds.algorithms.similarity.SimilarityAlgorithmsStatsBusinessFacade;
import org.neo4j.gds.algorithms.similarity.SimilarityAlgorithmsStreamBusinessFacade;
import org.neo4j.gds.algorithms.similarity.SimilarityAlgorithmsWriteBusinessFacade;
import org.neo4j.gds.api.AlgorithmMetaDataSetter;
import org.neo4j.gds.api.DatabaseId;
import org.neo4j.gds.api.ProcedureReturnColumns;
import org.neo4j.gds.api.User;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.procedures.community.ConfigurationParser;
import org.neo4j.gds.procedures.similarity.knn.KnnMutateResult;
import org.neo4j.gds.procedures.similarity.knn.KnnStatsResult;
import org.neo4j.gds.procedures.similarity.knn.KnnWriteResult;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.gds.similarity.SimilarityResult;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityMutateConfig;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityStatsConfig;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityStreamConfig;
import org.neo4j.gds.similarity.filterednodesim.FilteredNodeSimilarityWriteConfig;
import org.neo4j.gds.similarity.knn.KnnMutateConfig;
import org.neo4j.gds.similarity.knn.KnnStatsConfig;
import org.neo4j.gds.similarity.knn.KnnStreamConfig;
import org.neo4j.gds.similarity.knn.KnnWriteConfig;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityMutateConfig;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityStatsConfig;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityStreamConfig;
import org.neo4j.gds.similarity.nodesim.NodeSimilarityWriteConfig;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class SimilarityProcedureFacade {

    private final ConfigurationParser configurationParser;
    private final DatabaseId databaseId;
    private final User user;
    private final SimilarityAlgorithmsMutateBusinessFacade mutateBusinessFacade;
    private final SimilarityAlgorithmsStatsBusinessFacade statsBusinessFacade;
    private final SimilarityAlgorithmsStreamBusinessFacade streamBusinessFacade;
    private final SimilarityAlgorithmsWriteBusinessFacade writeBusinessFacade;
    private final ProcedureReturnColumns procedureReturnColumns;

    private final SimilarityAlgorithmsEstimateBusinessFacade estimateBusinessFacade;
    private final AlgorithmMetaDataSetter algorithmMetaDataSetter;

    public SimilarityProcedureFacade(
        ConfigurationParser configurationParser,
        DatabaseId databaseId,
        User user,
        ProcedureReturnColumns procedureReturnColumns,
        SimilarityAlgorithmsMutateBusinessFacade mutateBusinessFacade,
        SimilarityAlgorithmsStatsBusinessFacade statsBusinessFacade,
        SimilarityAlgorithmsStreamBusinessFacade streamBusinessFacade,
        SimilarityAlgorithmsWriteBusinessFacade writeBusinessFacade,
        SimilarityAlgorithmsEstimateBusinessFacade estimateBusinessFacade,
        AlgorithmMetaDataSetter algorithmMetaDataSetter
    ) {
        this.configurationParser = configurationParser;
        this.databaseId = databaseId;
        this.user = user;
        this.procedureReturnColumns = procedureReturnColumns;
        this.mutateBusinessFacade = mutateBusinessFacade;
        this.statsBusinessFacade = statsBusinessFacade;
        this.streamBusinessFacade = streamBusinessFacade;
        this.writeBusinessFacade = writeBusinessFacade;
        this.estimateBusinessFacade = estimateBusinessFacade;
        this.algorithmMetaDataSetter = algorithmMetaDataSetter;
    }

    public Stream<SimilarityResult> nodeSimilarityStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var streamConfig = createStreamConfig(configuration, NodeSimilarityStreamConfig::of);

        var computationResult = streamBusinessFacade.nodeSimilarity(
            graphName,
            streamConfig,
            user,
            databaseId
        );

        return NodeSimilarityComputationResultTransformer.toStreamResult(computationResult);
    }

    public Stream<SimilarityStatsResult> nodeSimilarityStats(
        String graphName,
        Map<String, Object> configuration
    ) {
        var statsConfig = createConfig(configuration, NodeSimilarityStatsConfig::of);

        var computationResult = statsBusinessFacade.nodeSimilarity(
            graphName,
            statsConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(NodeSimilarityComputationResultTransformer.toStatsResult(computationResult, statsConfig));
    }

    public Stream<SimilarityWriteResult> nodeSimilarityWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        var statsConfig = createConfig(configuration, NodeSimilarityWriteConfig::of);

        var computationResult = writeBusinessFacade.nodeSimilarity(
            graphName,
            statsConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(NodeSimilarityComputationResultTransformer.toWriteResult(computationResult));
    }

    public Stream<SimilarityMutateResult> nodeSimilarityMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        var mutateConfig = createConfig(configuration, NodeSimilarityMutateConfig::of);

        var computationResult = mutateBusinessFacade.nodeSimilarity(
            graphName,
            mutateConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(NodeSimilarityComputationResultTransformer.toMutateResult(computationResult));
    }


    public Stream<MemoryEstimateResult> nodeSimilarityEstimateStream(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, NodeSimilarityStreamConfig::of);
        return Stream.of(estimateBusinessFacade.nodeSimilarity(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> nodeSimilarityEstimateStats(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, NodeSimilarityStatsConfig::of);
        return Stream.of(estimateBusinessFacade.nodeSimilarity(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> nodeSimilarityEstimateMutate(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, NodeSimilarityMutateConfig::of);
        return Stream.of(estimateBusinessFacade.nodeSimilarity(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> nodeSimilarityEstimateWrite(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, NodeSimilarityWriteConfig::of);
        return Stream.of(estimateBusinessFacade.nodeSimilarity(graphNameOrConfiguration, config));
    }

    //filtered
    public Stream<SimilarityResult> filteredNodeSimilarityStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var streamConfig = createStreamConfig(configuration, FilteredNodeSimilarityStreamConfig::of);

        var computationResult = streamBusinessFacade.filteredNodeSimilarity(
            graphName,
            streamConfig,
            user,
            databaseId
        );

        return NodeSimilarityComputationResultTransformer.toStreamResult(computationResult);
    }

    public Stream<SimilarityStatsResult> filteredNodeSimilarityStats(
        String graphName,
        Map<String, Object> configuration
    ) {
        var statsConfig = createConfig(configuration, FilteredNodeSimilarityStatsConfig::of);

        var computationResult = statsBusinessFacade.filteredNodeSimilarity(
            graphName,
            statsConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(NodeSimilarityComputationResultTransformer.toStatsResult(computationResult, statsConfig));
    }

    public Stream<SimilarityMutateResult> filteredNodeSimilarityMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        var mutateConfig = createConfig(configuration, FilteredNodeSimilarityMutateConfig::of);

        var computationResult = mutateBusinessFacade.filteredNodeSimilarity(
            graphName,
            mutateConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(NodeSimilarityComputationResultTransformer.toMutateResult(computationResult));
    }

    public Stream<SimilarityWriteResult> filteredNodeSimilarityWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        var writeConfig = createConfig(configuration, FilteredNodeSimilarityWriteConfig::of);

        var computationResult = writeBusinessFacade.filteredNodeSimilarity(
            graphName,
            writeConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(NodeSimilarityComputationResultTransformer.toWriteResult(computationResult));
    }


    public Stream<MemoryEstimateResult> filteredNodeSimilarityEstimateStream(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, FilteredNodeSimilarityStreamConfig::of);
        return Stream.of(estimateBusinessFacade.nodeSimilarity(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> filteredNodeSimilarityEstimateStats(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, FilteredNodeSimilarityStatsConfig::of);
        return Stream.of(estimateBusinessFacade.nodeSimilarity(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> filteredNodeSimilarityEstimateMutate(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, FilteredNodeSimilarityMutateConfig::of);
        return Stream.of(estimateBusinessFacade.nodeSimilarity(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> filteredNodeSimilarityEstimateWrite(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, FilteredNodeSimilarityWriteConfig::of);
        return Stream.of(estimateBusinessFacade.nodeSimilarity(graphNameOrConfiguration, config));
    }

    public Stream<SimilarityResult> knnStream(
        String graphName,
        Map<String, Object> configuration
    ) {
        var streamConfig = createStreamConfig(configuration, KnnStreamConfig::of);

        var computationResult = streamBusinessFacade.knn(
            graphName,
            streamConfig,
            user,
            databaseId
        );

        return KnnComputationResultTransformer.toStreamResult(computationResult);
    }

    public Stream<KnnStatsResult> knnStats(
        String graphName,
        Map<String, Object> configuration
    ) {
        var statsConfig = createConfig(configuration, KnnStatsConfig::of);

        var computationResult = statsBusinessFacade.knn(
            graphName,
            statsConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(KnnComputationResultTransformer.toStatsResult(computationResult, statsConfig));
    }

    public Stream<KnnWriteResult> knnWrite(
        String graphName,
        Map<String, Object> configuration
    ) {
        var writeConfig = createConfig(configuration, KnnWriteConfig::of);

        var computationResult = writeBusinessFacade.knn(
            graphName,
            writeConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(KnnComputationResultTransformer.toWriteResult(computationResult, writeConfig));
    }

    public Stream<KnnMutateResult> knnMutate(
        String graphName,
        Map<String, Object> configuration
    ) {
        var mutateConfig = createConfig(configuration, KnnMutateConfig::of);

        var computationResult = mutateBusinessFacade.knn(
            graphName,
            mutateConfig,
            user,
            databaseId,
            procedureReturnColumns.contains("similarityDistribution")
        );

        return Stream.of(KnnComputationResultTransformer.toMutateResult(computationResult, mutateConfig));
    }


    public Stream<MemoryEstimateResult> knnStreamEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, KnnStreamConfig::of);
        return Stream.of(estimateBusinessFacade.knn(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> knnStatsEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, KnnStatsConfig::of);
        return Stream.of(estimateBusinessFacade.knn(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> knnWriteEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, KnnWriteConfig::of);
        return Stream.of(estimateBusinessFacade.knn(graphNameOrConfiguration, config));
    }

    public Stream<MemoryEstimateResult> knnMutateEstimate(
        Object graphNameOrConfiguration,
        Map<String, Object> algoConfiguration
    ) {
        var config = createConfig(algoConfiguration, KnnMutateConfig::of);
        return Stream.of(estimateBusinessFacade.knn(graphNameOrConfiguration, config));
    }


    // FIXME: the following two methods are duplicate, find a good place for them.
    private <C extends AlgoBaseConfig> C createStreamConfig(
        Map<String, Object> configuration,
        Function<CypherMapWrapper, C> configCreator
    ) {
        return createConfig(
            configuration,
            configCreator.andThen(algorithmConfiguration -> {
                algorithmMetaDataSetter.set(algorithmConfiguration);
                return algorithmConfiguration;
            })
        );
    }

    private <C extends AlgoBaseConfig> C createConfig(
        Map<String, Object> configuration,
        Function<CypherMapWrapper, C> configCreator
    ) {
        return configurationParser.produceConfig(configuration, configCreator, user.getUsername());
    }
    //FIXME: here ends the fixme-block
}