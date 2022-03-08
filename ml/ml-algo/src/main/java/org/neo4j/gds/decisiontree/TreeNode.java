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
package org.neo4j.gds.decisiontree;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

class TreeNode<PREDICTION> {
    public PREDICTION prediction;
    public int index = -1;
    public double value;
    public TreeNode<PREDICTION> leftChild = null;
    public TreeNode<PREDICTION> rightChild = null;

    TreeNode(int index, double value) {
        assert index >= 0;

        this.index = index;
        this.value = value;
    }

    TreeNode(PREDICTION prediction) {
        this.prediction = prediction;
    }

    @Override
    public String toString() {
        return formatWithLocale("Node: prediction %s, featureIndex %s, splitValue %f", this.prediction, this.index, this.value);
    }

    /**
     * Renders the variable into a human readable representation.
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        render(sb, this, 0);
        return sb.toString();
    }


    static void render(StringBuilder sb, TreeNode<?> node, int depth) {
        if (node == null) {
            return;
        }

        sb.append("\t".repeat(Math.max(0, depth - 1)));

        if (depth > 0) {
            sb.append("|-- ");
        }

        sb.append(node);
        sb.append(System.lineSeparator());

        render(sb, node.leftChild, depth + 1);
        render(sb, node.rightChild, depth + 1);
    }
}
