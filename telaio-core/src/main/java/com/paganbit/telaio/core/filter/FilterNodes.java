package com.paganbit.telaio.core.filter;

import com.turkraft.springfilter.parser.node.CollectionLikeNode;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only helpers over a parsed Turkraft {@link FilterNode} tree.
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public final class FilterNodes {

    private FilterNodes() {
    }

    /**
     * Collects every field reference of a filter tree, in depth-first order — the operands of operators,
     * the arguments of functions and the items of collections included.
     *
     * @param filter the parsed filter tree
     * @return the {@link FieldNode}s the tree references, possibly empty, never {@code null}
     */
    public static List<FieldNode> fieldNodes(FilterNode filter) {
        List<FieldNode> fields = new ArrayList<>();
        collect(filter, fields);
        return List.copyOf(fields);
    }

    private static void collect(FilterNode node, List<FieldNode> fields) {
        if (node instanceof FieldNode field) {
            fields.add(field);
            return;
        }
        for (FilterNode child : node.getChildren()) {
            collect(child, fields);
        }
        if (node instanceof CollectionLikeNode like) {
            // Its children expose only the left operand; the patterns may reference fields too.
            for (FilterNode pattern : like.getPatterns()) {
                collect(pattern, fields);
            }
        }
    }
}
