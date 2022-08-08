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
package org.neo4j.gds.graphsampling.samplers;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.DoubleCollection;
import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.LongCollection;
import com.carrotsearch.hppc.LongHashSet;
import com.carrotsearch.hppc.LongSet;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableLong;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.RunWithConcurrency;
import org.neo4j.gds.core.utils.paged.HugeAtomicBitSet;
import org.neo4j.gds.core.utils.paged.HugeAtomicDoubleArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.graphsampling.config.RandomWalkWithRestartsConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

public class RandomWalkWithRestarts implements NodesSampler {
    private static final String SAMPLING_TASK_NAME = "Sample with RWR";
    private static final double QUALITY_MOMENTUM = 0.9;
    private static final double QUALITY_THRESHOLD_BASE = 0.05;
    private static final int MAX_WALKS_PER_START = 100;
    private static final double TOTAL_WEIGHT_MISSING = -1.0;
    private static final long INVALID_NODE_ID = -1;

    private final RandomWalkWithRestartsConfig config;
    private LongHashSet startNodesUsed;

    public RandomWalkWithRestarts(RandomWalkWithRestartsConfig config) {
        this.config = config;
    }

    @Override
    public HugeAtomicBitSet compute(Graph inputGraph, ProgressTracker progressTracker) {
        assert inputGraph.hasRelationshipProperty() == config.hasRelationshipWeightProperty();

        progressTracker.beginSubTask(SAMPLING_TASK_NAME);

        progressTracker.setSteps((long) Math.ceil(inputGraph.nodeCount() * config.samplingRatio()));

        var seenNodes = getSeenNodes(inputGraph);
        startNodesUsed = new LongHashSet();
        var rng = new SplittableRandom(config.randomSeed().orElseGet(() -> new SplittableRandom().nextLong()));
        var initialStartQualities = initializeQualities(inputGraph, rng);
        Optional<HugeAtomicDoubleArray> totalWeights = initializeTotalWeights(inputGraph.nodeCount());

        var tasks = ParallelUtil.tasks(config.concurrency(), () ->
            new Walker(
                seenNodes,
                totalWeights,
                QUALITY_THRESHOLD_BASE / (config.concurrency() * config.concurrency()),
                new WalkQualities(initialStartQualities),
                rng.split(),
                inputGraph.concurrentCopy(),
                config,
                progressTracker
            )
        );
        RunWithConcurrency.builder()
            .concurrency(config.concurrency())
            .tasks(tasks)
            .run();
        tasks.forEach(task -> startNodesUsed.addAll(((Walker) task).startNodesUsed()));

        progressTracker.endSubTask(SAMPLING_TASK_NAME);

        return seenNodes.sampledNodes();
    }

    @Override
    public Task progressTask(GraphStore graphStore) {
        return Tasks.leaf(SAMPLING_TASK_NAME, 10 * Math.round(graphStore.nodeCount() * config.samplingRatio()));
    }

    @Override
    public String progressTaskName() {
        return "Random walk with restarts sampling";
    }

    private SeenNodes getSeenNodes(Graph inputGraph) {
        long totalExpectedCount = Math.round(inputGraph.nodeCount() * config.samplingRatio());

        if (config.nodeLabelStratification()) {
            var expectedCounts = getExpectedCountsPerNodeLabelSet(inputGraph);
            return new SeenNodesByLabelSet(inputGraph, expectedCounts, totalExpectedCount);
        }

        return new GlobalSeenNodes(
            HugeAtomicBitSet.create(inputGraph.nodeCount()),
            totalExpectedCount
        );
    }

    private Map<Set<NodeLabel>, Long> getExpectedCountsPerNodeLabelSet(Graph inputGraph) {
        var counts = new HashMap<Set<NodeLabel>, Long>();

        inputGraph.forEachNode(nodeId -> {
            // TODO: Can we avoid GC overhead here somehow?
            var nodeLabelSet = new HashSet<>(inputGraph.nodeLabels(nodeId));
            counts.put(nodeLabelSet, 1L + counts.getOrDefault(nodeLabelSet, 0L));
            return true;
        });
        // We round up so that the sum of all expected counts are at least as large as the total expected count.
        counts.replaceAll((unused, count) -> (long) Math.ceil(config.samplingRatio() * count));

        return counts;
    }

    private Optional<HugeAtomicDoubleArray> initializeTotalWeights(long nodeCount) {
        if (config.hasRelationshipWeightProperty()) {
            var totalWeights = HugeAtomicDoubleArray.newArray(nodeCount);
            totalWeights.setAll(TOTAL_WEIGHT_MISSING);
            return Optional.of(totalWeights);
        }
        return Optional.empty();
    }

    @ValueClass
    interface InitialStartQualities {
        LongCollection nodeIds();

        DoubleCollection qualities();
    }

    private InitialStartQualities initializeQualities(Graph inputGraph, SplittableRandom rng) {
        var nodeIds = new LongArrayList();
        var qualities = new DoubleArrayList();

        if (!config.startNodes().isEmpty()) {
            config.startNodes().forEach(nodeId -> {
                nodeIds.add(inputGraph.toMappedNodeId(nodeId));
                qualities.add(1.0);
            });
        } else {
            nodeIds.add(rng.nextLong(inputGraph.nodeCount()));
            qualities.add(1.0);
        }

        return ImmutableInitialStartQualities.of(nodeIds, qualities);
    }

    public LongSet startNodesUsed() {
        return startNodesUsed;
    }

    static class Walker implements Runnable {

        private final SeenNodes seenNodes;
        private final Optional<HugeAtomicDoubleArray> totalWeights;
        private final double qualityThreshold;
        private final WalkQualities walkQualities;
        private final SplittableRandom rng;
        private final Graph inputGraph;
        private final RandomWalkWithRestartsConfig config;
        private final ProgressTracker progressTracker;

        private final LongSet startNodesUsed;

        Walker(
            SeenNodes seenNodes,
            Optional<HugeAtomicDoubleArray> totalWeights,
            double qualityThreshold,
            WalkQualities walkQualities,
            SplittableRandom rng,
            Graph inputGraph,
            RandomWalkWithRestartsConfig config,
            ProgressTracker progressTracker
        ) {
            this.seenNodes = seenNodes;
            this.totalWeights = totalWeights;
            this.qualityThreshold = qualityThreshold;
            this.walkQualities = walkQualities;
            this.rng = rng;
            this.inputGraph = inputGraph;
            this.config = config;
            this.progressTracker = progressTracker;
            this.startNodesUsed = new LongHashSet();
        }

        @Override
        public void run() {
            int currentStartNodePosition = rng.nextInt(walkQualities.size());
            long currentNode = walkQualities.nodeId(currentStartNodePosition);
            startNodesUsed.add(inputGraph.toOriginalNodeId(currentNode));
            int addedNodes = 0;
            int nodesConsidered = 1;
            int walksLeft = (int) Math.round(walkQualities.nodeQuality(currentStartNodePosition) * MAX_WALKS_PER_START);

            while (!seenNodes.hasSeenEnough()) {
                if (seenNodes.addNode(currentNode)) {
                    addedNodes++;
                }

                // walk a step
                double degree = computeDegree(currentNode);
                if (degree == 0.0 || rng.nextDouble() < config.restartProbability()) {
                    progressTracker.logSteps(addedNodes);

                    double walkQuality = ((double) addedNodes) / nodesConsidered;
                    walkQualities.updateNodeQuality(currentStartNodePosition, walkQuality);
                    addedNodes = 0;
                    nodesConsidered = 1;

                    if (walksLeft-- > 0 && walkQualities.nodeQuality(currentStartNodePosition) > qualityThreshold) {
                        currentNode = walkQualities.nodeId(currentStartNodePosition);
                        continue;
                    }

                    if (walkQualities.nodeQuality(currentStartNodePosition) < 1.0 / MAX_WALKS_PER_START) {
                        walkQualities.removeNode(currentStartNodePosition);
                    }

                    if (walkQualities.expectedQuality() < qualityThreshold) {
                        long newNode;
                        do {
                            newNode = rng.nextLong(inputGraph.nodeCount());
                        } while (!walkQualities.addNode(newNode));
                    }

                    currentStartNodePosition = rng.nextInt(walkQualities.size());
                    currentNode = walkQualities.nodeId(currentStartNodePosition);
                    startNodesUsed.add(inputGraph.toOriginalNodeId(currentNode));
                    walksLeft = (int) Math.round(walkQualities.nodeQuality(currentStartNodePosition) * MAX_WALKS_PER_START);
                } else {
                    if (totalWeights.isPresent()) {
                        currentNode = weightedNextNode(currentNode);
                    } else {
                        int targetOffset = rng.nextInt(inputGraph.degree(currentNode));
                        currentNode = inputGraph.nthTarget(currentNode, targetOffset);
                        assert currentNode != IdMap.NOT_FOUND : "The offset '" + targetOffset + "' is bound by the degree but no target could be found for nodeId " + currentNode;
                    }
                    nodesConsidered++;
                }
            }
        }

        private double computeDegree(long currentNode) {
            if (totalWeights.isEmpty()) {
                return inputGraph.degree(currentNode);
            }

            var presentTotalWeights = totalWeights.get();
            if (presentTotalWeights.get(currentNode) == TOTAL_WEIGHT_MISSING) {
                var degree = new MutableDouble(0.0);
                inputGraph.forEachRelationship(currentNode, 0.0, (src, trg, weight) -> {
                    degree.add(weight);
                    return true;
                });
                presentTotalWeights.set(currentNode, degree.doubleValue());
            }

            return presentTotalWeights.get(currentNode);
        }

        private long weightedNextNode(long currentNode) {
            var remainingMass = new MutableDouble(rng.nextDouble(0, computeDegree(currentNode)));
            var target = new MutableLong(INVALID_NODE_ID);

            inputGraph.forEachRelationship(currentNode, 0.0, (src, trg, weight) -> {
                if (remainingMass.doubleValue() < weight) {
                    target.setValue(trg);
                    return false;
                }
                remainingMass.subtract(weight);
                return true;
            });

            assert target.getValue() != -1;

            return target.getValue();
        }

        public LongSet startNodesUsed() {
            return startNodesUsed;
        }
    }

    /**
     * In order be able to sample start nodes uniformly at random (for performance reasons) we have a special data
     * structure which is optimized for exactly this. In particular, we need to be able to do random access by index
     * of the set of start nodes we are currently interested in. A simple hashmap for example does not work for this
     * reason.
     */
    static class WalkQualities {
        private final LongSet nodeIdIndex;
        private final LongArrayList nodeIds;
        private final DoubleArrayList qualities;
        private int size;
        private double sum;
        private double sumOfSquares;

        WalkQualities(InitialStartQualities initialStartQualities) {
            this.nodeIdIndex = new LongHashSet(initialStartQualities.nodeIds());
            this.nodeIds = new LongArrayList(initialStartQualities.nodeIds());
            this.qualities = new DoubleArrayList(initialStartQualities.qualities());
            this.sum = qualities.size();
            this.sumOfSquares = qualities.size();
            this.size = qualities.size();
        }

        boolean addNode(long nodeId) {
            if (nodeIdIndex.contains(nodeId)) {
                return false;
            }

            if (size >= nodeIds.size()) {
                nodeIds.add(nodeId);
                qualities.add(1.0);
            } else {
                nodeIds.set(size, nodeId);
                qualities.set(size, 1.0);
            }
            nodeIdIndex.add(nodeId);
            size++;

            sum += 1.0;
            sumOfSquares += 1.0;

            return true;
        }

        void removeNode(int position) {
            double quality = qualities.get(position);
            sum -= quality;
            sumOfSquares -= quality * quality;

            nodeIds.set(position, nodeIds.get(size - 1));
            qualities.set(position, qualities.get(size - 1));
            size--;
        }

        long nodeId(int position) {
            return nodeIds.get(position);
        }

        double nodeQuality(int position) {
            return qualities.get(position);
        }

        void updateNodeQuality(int position, double walkQuality) {
            double previousQuality = qualities.get(position);
            double updatedQuality = QUALITY_MOMENTUM * previousQuality + (1 - QUALITY_MOMENTUM) * walkQuality;

            sum += updatedQuality - previousQuality;
            sumOfSquares += updatedQuality * updatedQuality - previousQuality * previousQuality;

            qualities.set(position, updatedQuality);
        }

        double expectedQuality() {
            if (size <= 0) {
                return 0;
            }
            return sumOfSquares / sum;
        }

        int size() {
            return size;
        }
    }

    interface SeenNodes {
        /**
         * Tries to add a node to the sample.
         * Returns true iff it succeeded in doing so.
         */
        boolean addNode(long nodeId);

        boolean hasSeenEnough();

        HugeAtomicBitSet sampledNodes();
    }

    static class SeenNodesByLabelSet implements SeenNodes {
        private final Graph inputGraph;
        private final Map<Set<NodeLabel>, Long> seenPerLabelSet;
        private final Map<Set<NodeLabel>, Long> expectedNodesPerLabelSet;
        private final HugeAtomicBitSet seenBitSet;
        private final long totalExpectedNodes;

        SeenNodesByLabelSet(
            Graph inputGraph,
            Map<Set<NodeLabel>, Long> expectedNodesPerLabelSet,
            long totalExpectedNodes
        ) {
            this.inputGraph = inputGraph;
            this.expectedNodesPerLabelSet = expectedNodesPerLabelSet;
            this.seenBitSet = HugeAtomicBitSet.create(inputGraph.nodeCount());
            this.totalExpectedNodes = totalExpectedNodes;

            this.seenPerLabelSet = new HashMap<>(expectedNodesPerLabelSet);
            this.seenPerLabelSet.replaceAll((unused, value) -> 0L);
        }

        public boolean addNode(long nodeId) {
            var labelSet = new HashSet<>(inputGraph.nodeLabels(nodeId));
            // There's a slight race condition here which may cause there to be an extra node or two in a given
            // node label set bucket, since the cardinality check and the set are not synchronized together.
            // Since the sampling is inexact by nature this should be fine.
            if (seenPerLabelSet.get(labelSet) < expectedNodesPerLabelSet.get(labelSet)) {
                boolean added = !seenBitSet.getAndSet(nodeId);
                if (added) {
                    seenPerLabelSet.compute(labelSet, (unused, count) -> count + 1);
                }
                return added;
            }

            return false;
        }

        public boolean hasSeenEnough() {
            if (seenBitSet.cardinality() < totalExpectedNodes) {
                return false;
            }

            for (var entry : seenPerLabelSet.entrySet()) {
                if (entry.getValue() < expectedNodesPerLabelSet.get(entry.getKey())) {
                    // Should only happen is edge cases when the rounding is not fully consistent.
                    return false;
                }
            }

            return true;
        }

        public HugeAtomicBitSet sampledNodes() {
            return seenBitSet;
        }
    }

    static class GlobalSeenNodes implements SeenNodes {
        private final HugeAtomicBitSet seenBitSet;
        private final long expectedNodes;

        GlobalSeenNodes(HugeAtomicBitSet seenBitSet, long expectedNodes) {
            this.seenBitSet = seenBitSet;
            this.expectedNodes = expectedNodes;
        }

        public boolean addNode(long nodeId) {
            return !seenBitSet.getAndSet(nodeId);
        }

        public boolean hasSeenEnough() {
            return seenBitSet.cardinality() >= expectedNodes;
        }

        public HugeAtomicBitSet sampledNodes() {
            return seenBitSet;
        }
    }
}
