package com.paganbit.telaio.core.filter;

import com.turkraft.springfilter.definition.*;
import com.turkraft.springfilter.parser.node.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link FilterNodes}: every {@code FieldNode} of a tree is collected, in depth-first
 * order, whatever node kind carries it — including the patterns of a {@code CollectionLikeNode}, which
 * the node does not expose as children. Operators and functions are mocked: only the tree shape matters.
 */
class FilterNodesTest {

    private final FilterInfixOperator infix = mock(FilterInfixOperator.class);
    private final FilterPrefixOperator prefix = mock(FilterPrefixOperator.class);
    private final FilterPostfixOperator postfix = mock(FilterPostfixOperator.class);
    private final FilterFunction function = mock(FilterFunction.class);

    @Test
    void returnsTheFieldItselfForABareFieldNode() {
        FieldNode field = new FieldNode("name");

        assertThat(FilterNodes.fieldNodes(field)).containsExactly(field);
    }

    @Test
    void returnsEmptyForATreeWithoutFieldReferences() {
        FilterNode literals = new InfixOperationNode(new InputNode(1), infix, new InputNode(2));

        assertThat(FilterNodes.fieldNodes(literals)).isEmpty();
        assertThat(FilterNodes.fieldNodes(new InputNode("x"))).isEmpty();
        assertThat(FilterNodes.fieldNodes(new PlaceholderNode(mock(FilterPlaceholder.class)))).isEmpty();
    }

    @Test
    void collectsFieldsDepthFirstAcrossEveryNodeKind() {
        // (a > 1 and not (b in [2, c])) or (f(d.e, 3) is null)
        FilterNode comparison = new InfixOperationNode(new FieldNode("a"), infix, new InputNode(1));
        FilterNode membership = new InfixOperationNode(
            new FieldNode("b"), infix, new CollectionNode(List.of(new InputNode(2), new FieldNode("c"))));
        FilterNode negated = new PrefixOperationNode(prefix, new PriorityNode(membership));
        FilterNode call = new FunctionNode(function, List.of(new FieldNode("d.e"), new InputNode(3)));
        FilterNode nullCheck = new PostfixOperationNode(call, postfix);
        FilterNode root = new InfixOperationNode(
            new PriorityNode(new InfixOperationNode(comparison, infix, negated)), infix, nullCheck);

        assertThat(FilterNodes.fieldNodes(root))
            .extracting(FieldNode::getName)
            .containsExactly("a", "b", "c", "d.e");
    }

    @Test
    void includesThePatternsOfCollectionLikeNodes() {
        // `left ~ ['a*', other]`: the node's children expose the left operand only.
        CollectionLikeNode like = new CollectionLikeNode(
            new FieldNode("left"), infix, List.of(new InputNode("a*"), new FieldNode("other")));
        assertThat(like.getChildren()).hasSize(1);

        assertThat(FilterNodes.fieldNodes(like))
            .extracting(FieldNode::getName)
            .containsExactly("left", "other");
    }

    @Test
    void returnsAnUnmodifiableList() {
        List<FieldNode> fields = FilterNodes.fieldNodes(new FieldNode("name"));
        FieldNode other = new FieldNode("other");

        assertThatThrownBy(() -> fields.add(other))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
