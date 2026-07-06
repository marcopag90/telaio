package com.paganbit.telaio.core.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turkraft.springfilter.definition.FilterFunction;
import com.turkraft.springfilter.definition.FilterInfixOperator;
import com.turkraft.springfilter.definition.FilterPrefixOperator;
import com.turkraft.springfilter.parser.node.*;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link JsonFieldNameFilterRewriter}: only {@code FieldNode} names are translated from
 * their JSON wire name to the Java property name; the tree shape, operators, functions, and literals are
 * preserved. Operators/functions are mocked since their identity, not behavior, is what must survive.
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
    void rewritesFieldsInsidePrefixAndPriorityWrappers() {
        FilterPrefixOperator not = mock(FilterPrefixOperator.class);
        PrefixOperationNode node = new PrefixOperationNode(not, new PriorityNode(new FieldNode("cost_price")));

        PrefixOperationNode result = (PrefixOperationNode) rewriter.rewrite(node, Product.class);

        assertThat(result.getOperator()).isSameAs(not);
        PriorityNode priority = (PriorityNode) result.getRight();
        assertThat(((FieldNode) priority.getNode()).getName()).isEqualTo("costPrice");
    }

    @Getter
    @Setter
    private static class Product {
        private String name;
        @JsonProperty("cost_price")
        private BigDecimal costPrice;
    }
}
