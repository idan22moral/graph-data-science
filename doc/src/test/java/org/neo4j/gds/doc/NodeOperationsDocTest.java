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
package org.neo4j.gds.doc;

import org.neo4j.gds.catalog.GraphCreateProc;
import org.neo4j.gds.catalog.GraphRemoveNodePropertiesProc;
import org.neo4j.gds.catalog.GraphStreamNodePropertiesProc;
import org.neo4j.gds.catalog.GraphWriteNodePropertiesProc;
import org.neo4j.gds.degree.DegreeCentralityMutateProc;
import org.neo4j.gds.functions.AsNodeFunc;
import org.neo4j.gds.functions.NodePropertyFunc;

import java.util.List;

final class NodeOperationsDocTest extends DocTestBase {

    @Override
    protected List<Class<?>> procedures() {
        return List.of(
            GraphCreateProc.class,
            DegreeCentralityMutateProc.class,
            GraphStreamNodePropertiesProc.class,
            GraphWriteNodePropertiesProc.class,
            GraphRemoveNodePropertiesProc.class
        );
    }

    @Override
    List<Class<?>> functions() {
        return List.of(
            NodePropertyFunc.class,
            AsNodeFunc.class
        );
    }

    @Override
    protected String adocFile() {
        return "pages/graph-catalog-node-ops.adoc";
    }

}
