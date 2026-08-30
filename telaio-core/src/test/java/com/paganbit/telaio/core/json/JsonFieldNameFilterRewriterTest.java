package com.paganbit.telaio.core.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.turkraft.springfilter.definition.*;
import com.turkraft.springfilter.parser.node.*;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link JsonFieldNameFilterRewriter}: only {@code FieldNode} names are translated from
 * their JSON wire name to the Java property name; the tree shape, operators, functions, and literals are
 * preserved, and a field the entity does not expose is rejected. Operators/functions are mocked since
 * their identity, not behavior, is what must survive.
 */
class JsonFieldNameFilterRewriterTest {

    private final JsonFieldNameFilterRewriter rewriter =
        new JsonFieldNameFilterRewriter(new JsonPropertyPathResolver(JsonMapper.builder().build()));

    @Test
    void translatesRenamedFieldName() {
        FieldNode rewritten = (FieldNode) rewriter.rewrite(new FieldNode("cost_price"), Product.class);
        assertThat(rewritten.getName()).isEqualTo("costPrice");
    }

    @Test
    void leavesNonRenamedAndJavaNamesUnchanged() {
        assertThat(((FieldNode) rewriter.rewrite(new FieldNode("name"), Product.class)).getName())
            .isEqualTo("name");
        // A filter already using the Java attribute name must keep working.
        assertThat(((FieldNode) rewriter.rewrite(new FieldNode("costPrice"), Product.class)).getName())
            .isEqualTo("costPrice");
    }

    @Test
    void rejectsUnknownRootField() {
        FieldNode unknown = new FieldNode("not_a_field");

        assertThatThrownBy(() -> rewriter.rewrite(unknown, Product.class))
            .isInstanceOf(DalInvalidFilterException.class)
            .hasMessageContaining("not_a_field");
    }

    @Test
    void rejectsUnknownNestedFieldButNamesTheOffendingSegment() {
        FieldNode unknownNested = new FieldNode("dims.depth");

        assertThatThrownBy(() -> rewriter.rewrite(unknownNested, Product.class))
            .isInstanceOf(DalInvalidFilterException.class)
            .hasMessageContaining("'depth'")
            .hasMessageContaining("'dims.depth'");
    }

    @Test
    void passesThroughMapKeysAndDollarSegments() {
        // Map keys are dynamic, "$id" is a backend accessor: neither is checked against the bean.
        assertThat(((FieldNode) rewriter.rewrite(new FieldNode("attributes.color"), Product.class)).getName())
            .isEqualTo("attributes.color");
        assertThat(((FieldNode) rewriter.rewrite(new FieldNode("dims.$id"), Product.class)).getName())
            .isEqualTo("dims.$id");
    }

    @Test
    void rejectsUnknownFieldInsideFunctionArguments() {
        FilterFunction function = mock(FilterFunction.class);
        FunctionNode node = new FunctionNode(function, List.of(new FieldNode("nope")));

        assertThatThrownBy(() -> rewriter.rewrite(node, Product.class))
            .isInstanceOf(DalInvalidFilterException.class);
    }

    @Test
    void rewritesFieldsInsideInfixTreeWithoutTouchingLiteralsOrOperators() {
        FilterInfixOperator and = mock(FilterInfixOperator.class);
        FilterInfixOperator gt = mock(FilterInfixOperator.class);
        FilterInfixOperator eq = mock(FilterInfixOperator.class);

        InfixOperationNode left = new InfixOperationNode(new FieldNode("cost_price"), gt, new InputNode(100));
        InfixOperationNode right = new InfixOperationNode(new FieldNode("name"), eq, new InputNode("x"));
        InfixOperationNode root = new InfixOperationNode(left, and, right);

        InfixOperationNode result = (InfixOperationNode) rewriter.rewrite(root, Product.class);

        // Identity preserves operators, structure unchanged.
        assertThat(result.getOperator()).isSameAs(and);
        InfixOperationNode resultLeft = (InfixOperationNode) result.getLeft();
        InfixOperationNode resultRight = (InfixOperationNode) result.getRight();
        assertThat(resultLeft.getOperator()).isSameAs(gt);
        assertThat(resultRight.getOperator()).isSameAs(eq);

        // Field names translated, literals untouched.
        assertThat(((FieldNode) resultLeft.getLeft()).getName()).isEqualTo("costPrice");
        assertThat(((InputNode) resultLeft.getRight()).getValue()).isEqualTo(100);
        assertThat(((FieldNode) resultRight.getLeft()).getName()).isEqualTo("name");
        assertThat(((InputNode) resultRight.getRight()).getValue()).isEqualTo("x");
    }

    @Test
    void rewritesFieldsInsideFunctionArguments() {
        FilterFunction function = mock(FilterFunction.class);
        FunctionNode node = new FunctionNode(function, List.of(new FieldNode("cost_price")));

        FunctionNode result = (FunctionNode) rewriter.rewrite(node, Product.class);

        assertThat(result.getFunction()).isSameAs(function);
        assertThat(((FieldNode) result.getArgument(0)).getName()).isEqualTo("costPrice");
    }

    @Test
    void rewritesFieldsInsideCollectionLikeNodes() {
        // `field ~ ['a', 'b']` parses to a CollectionLikeNode, not to an InfixOperationNode.
        FilterInfixOperator like = mock(FilterInfixOperator.class);
        CollectionLikeNode node = new CollectionLikeNode(
            new FieldNode("cost_price"), like, List.of(new InputNode("1*"), new FieldNode("name")));

        CollectionLikeNode result = (CollectionLikeNode) rewriter.rewrite(node, Product.class);

        assertThat(result.getOperator()).isSameAs(like);
        assertThat(((FieldNode) result.getLeft()).getName()).isEqualTo("costPrice");
        assertThat(((InputNode) result.getPatterns().get(0)).getValue()).isEqualTo("1*");
        assertThat(((FieldNode) result.getPatterns().get(1)).getName()).isEqualTo("name");
    }

    @Test
    void rewritesFieldsInsidePrefixAndPriorityWrappers() {
        FilterPrefixOperator not = mock(FilterPrefixOperator.class);
        PrefixOperationNode node = new PrefixOperationNode(not, new PriorityNode(new FieldNode("cost_price")));

        PrefixOperationNode result = (PrefixOperationNode) rewriter.rewrite(node, Product.class);

        assertThat(result.getOperator()).isSameAs(not);
        PriorityNode priority = (PriorityNode) result.getRight();
        assertThat(((FieldNode) priority.getNode()).getName()).isEqualTo("costPrice");
    }

    @Test
    void rewritesFieldInsidePostfixOperations() {
        // `field is null` parses to a PostfixOperationNode.
        FilterPostfixOperator isNull = mock(FilterPostfixOperator.class);
        PostfixOperationNode node = new PostfixOperationNode(new FieldNode("cost_price"), isNull);

        PostfixOperationNode result = (PostfixOperationNode) rewriter.rewrite(node, Product.class);

        assertThat(result.getOperator()).isSameAs(isNull);
        assertThat(((FieldNode) result.getLeft()).getName()).isEqualTo("costPrice");
    }

    @Test
    void rewritesFieldsInsideCollectionNodes() {
        // `field in [1, other]` parses to an InfixOperationNode whose right operand is a CollectionNode.
        FilterInfixOperator in = mock(FilterInfixOperator.class);
        InfixOperationNode node = new InfixOperationNode(new FieldNode("cost_price"), in,
            new CollectionNode(List.of(new InputNode(1), new FieldNode("name"))));

        InfixOperationNode result = (InfixOperationNode) rewriter.rewrite(node, Product.class);

        assertThat(result.getOperator()).isSameAs(in);
        assertThat(((FieldNode) result.getLeft()).getName()).isEqualTo("costPrice");
        CollectionNode items = (CollectionNode) result.getRight();
        assertThat(((InputNode) items.getItems().get(0)).getValue()).isEqualTo(1);
        assertThat(((FieldNode) items.getItems().get(1)).getName()).isEqualTo("name");
    }

    @Test
    void returnsLeavesWithoutFieldReferenceAsIs() {
        InputNode literal = new InputNode(42);
        PlaceholderNode placeholder = new PlaceholderNode(mock(FilterPlaceholder.class));

        assertThat(rewriter.rewrite(literal, Product.class)).isSameAs(literal);
        assertThat(rewriter.rewrite(placeholder, Product.class)).isSameAs(placeholder);
    }

    @Getter
    @Setter
    private static class Product {
        private String name;
        @JsonProperty("cost_price")
        private BigDecimal costPrice;
        private Dimensions dims;
        private Map<String, String> attributes;
    }

    @Getter
    @Setter
    private static class Dimensions {
        private int width;
    }
}
