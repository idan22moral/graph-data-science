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
package org.neo4j.gds.ml.core.functions;

import org.neo4j.gds.ml.core.AbstractVariable;
import org.neo4j.gds.ml.core.ComputationContext;
import org.neo4j.gds.ml.core.Variable;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.gds.ml.core.tensor.Scalar;
import org.neo4j.gds.ml.core.tensor.Tensor;
import org.neo4j.gds.ml.core.tensor.Vector;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.neo4j.gds.ml.core.Dimensions.scalar;

/**
 * Computes cross entropy loss given weights, bias, predictions, features and labels,
 * where it is assumed that predictions contain only values for all classes but the last one,
 * in practice, the output of ReducedSoftmax.
 */
public class ReducedCrossEntropyLoss extends AbstractVariable<Scalar> {

    private final Variable<Matrix> weights;
    private final Optional<Variable<Scalar>> bias;
    private final ReducedSoftmax predictions;
    private final Variable<Matrix> features;
    private final Variable<Vector> labels;

    public ReducedCrossEntropyLoss(
        Variable<Matrix> weights,
        ReducedSoftmax predictions,
        Variable<Matrix> features,
        Variable<Vector> labels,
        Optional<Variable<Scalar>> bias
    ) {
        super(
            bias.map(b -> List.of(weights, features, labels, b)).orElse(List.of(weights, features, labels)),
            scalar()
        );

        this.weights = weights;
        this.predictions = predictions;
        this.features = features;
        this.labels = labels;
        this.bias = bias;
    }

    public static long sizeInBytes() {
        return Scalar.sizeInBytes();
    }

    private double predictedProbabilityForClass(Matrix predictionsMatrix, int classIdx, int row) {
        if (classIdx == predictionsMatrix.cols()) {
            // the virtual class
            return 1 - IntStream
                .range(0, predictionsMatrix.cols())
                .mapToDouble(c -> predictionsMatrix.dataAt(row, c))
                .sum();
        }
        return predictionsMatrix.dataAt(row, classIdx);
    }

    @Override
    public Scalar apply(ComputationContext ctx) {
        var predictionsMatrix = ctx.forward(predictions);
        var labelsVector = ctx.data(labels);

        double result = 0;
        for (int row = 0; row < labelsVector.totalSize(); row++) {
            var trueClass = (int) labelsVector.dataAt(row);
            var predictedProbabilityForTrueClass = predictedProbabilityForClass(predictionsMatrix, trueClass, row);
            if (predictedProbabilityForTrueClass > 0) {
                result += Math.log(predictedProbabilityForTrueClass);
            }
        }
        return new Scalar(-result / predictionsMatrix.rows());
    }

    @Override
    public Tensor<?> gradient(Variable<?> parent, ComputationContext ctx) {
        var predMatrix = ctx.forward(predictions);
        var labelsVector = ctx.data(labels);
        int numberOfExamples = labelsVector.length();
        if (parent == weights) {
            var weightsMatrix = ctx.data(weights);
            var featureMatrix = ctx.data(features);
            var gradient = weightsMatrix.createWithSameDimensions();
            int featureCount = weightsMatrix.cols();
            int weightRows = weightsMatrix.rows();

            for (int row = 0; row < numberOfExamples; row++) {
                int trueClass = (int) labelsVector.dataAt(row);
                for (int classIdx = 0; classIdx < weightRows; classIdx++) {
                    double predictedClassProbability = predictedProbabilityForClass(predMatrix, classIdx, row);
                    var indicatorIsTrueClass = trueClass == classIdx ? 1.0 : 0.0;
                    var errorPerExample = (predictedClassProbability - indicatorIsTrueClass) / numberOfExamples;
                    for (int feature = 0; feature < featureCount; feature++) {
                        gradient.addDataAt(classIdx, feature, errorPerExample * featureMatrix.dataAt(row, feature));
                    }
                }
            }
            return gradient;
        } else if (bias.map(b -> parent == b).orElse(Boolean.FALSE)) {
            var gradient = new Scalar(0);
            for (int row = 0; row < numberOfExamples; row++) {
                int trueClass = (int) labelsVector.dataAt(row);
                for (int classIdx = 0; classIdx < weights.dimension(0); classIdx++) {
                    double predictedClassProbability = predictedProbabilityForClass(predMatrix, classIdx, row);
                    var indicatorIsTrueClass = trueClass == classIdx ? 1.0 : 0.0;
                    var errorPerExample = (predictedClassProbability - indicatorIsTrueClass) / numberOfExamples;
                    gradient.addDataAt(0, errorPerExample);
                }
            }
            return gradient;
        } else {
            // assume feature and label variables do not require gradient
            return ctx.data(parent).createWithSameDimensions();
        }
    }
}
