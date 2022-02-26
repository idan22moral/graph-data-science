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
package org.neo4j.gds.ml;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.IdFunction;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@GdlExtension
class FeaturesTest {

    @GdlGraph
    private static final String DB_QUERY =
        "CREATE " +
        "  (a:N {bananas: 100.0, arrayProperty: [1.2, 1.2], a: 1.2})" +
        ", (b:N {bananas: 100.0, arrayProperty: [1.32, 0.5], a: 1.32})" +
        ", (c:N {bananas: 100.0, arrayProperty: [1.3, 1.5], a: 1.3})" +
        ", (d:N {bananas: 100.0, arrayProperty: [5.3, 10.5], a: 5.3})" +
        ", (e:N {bananas: 100.0, arrayProperty: [1.0, 0.9], a: 1.0})";

    @Inject
    TestGraph graph;

    @Inject
    IdFunction idFunction;

    @Test
    void lazyFeaturesSingleScalar() {
        var features = Features.extractLazyFeatures(graph, List.of("a"));
        assertThat(features.get(idFunction.of("a"))).containsExactly(new double[]{1.2}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("b"))).containsExactly(new double[]{1.32}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("c"))).containsExactly(new double[]{1.3}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("d"))).containsExactly(new double[]{5.3}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("e"))).containsExactly(new double[]{1.0}, Offset.offset(1e-6));
    }

    @Test
    void lazyFeaturesSingleArray() {
        var features = Features.extractLazyFeatures(graph, List.of("arrayProperty"));
        assertThat(features.get(idFunction.of("a"))).containsExactly(new double[]{1.2, 1.2}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("b"))).containsExactly(new double[]{1.32, 0.5}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("c"))).containsExactly(new double[]{1.3, 1.5}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("d"))).containsExactly(new double[]{5.3, 10.5}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("e"))).containsExactly(new double[]{1.0, 0.9}, Offset.offset(1e-6));
    }

    @Test
    void lazyFeaturesArrayAndTwoScalars() {
        var features = Features.extractLazyFeatures(graph, List.of("bananas", "arrayProperty", "a"));
        assertThat(features.get(idFunction.of("a"))).containsExactly(new double[]{100.0, 1.2, 1.2, 1.2}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("b"))).containsExactly(new double[]{100.0, 1.32, 0.5, 1.32}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("c"))).containsExactly(new double[]{100.0, 1.3, 1.5, 1.3}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("d"))).containsExactly(new double[]{100.0, 5.3, 10.5, 5.3}, Offset.offset(1e-6));
        assertThat(features.get(idFunction.of("e"))).containsExactly(new double[]{100.0, 1.0, 0.9, 1.0}, Offset.offset(1e-6));
    }
}
