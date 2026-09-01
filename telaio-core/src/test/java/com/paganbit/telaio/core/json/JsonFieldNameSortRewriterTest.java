package com.paganbit.telaio.core.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.exception.DalInvalidSortException;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JsonFieldNameSortRewriter}: every order's property is translated from its JSON
 * wire name to the Java property name, direction and flags are preserved, and a property the entity
 * does not expose is rejected.
 */
class JsonFieldNameSortRewriterTest {

    private final JsonFieldNameSortRewriter rewriter =
        new JsonFieldNameSortRewriter(new JsonPropertyPathResolver(JsonMapper.builder().build()));

    @Test
    void translatesRenamedPropertyName() {
        Sort rewritten = rewriter.rewrite(Sort.by("cost_price"), Product.class);

        assertThat(rewritten.stream()).singleElement()
            .extracting(Sort.Order::getProperty).isEqualTo("costPrice");
    }

    @Test
    void leavesNonRenamedAndJavaNamesUnchanged() {
        assertThat(rewriter.rewrite(Sort.by("name"), Product.class).stream()).singleElement()
            .extracting(Sort.Order::getProperty).isEqualTo("name");
        // A sort already using the Java property name must keep working (e.g. a DAL's default sort).
        assertThat(rewriter.rewrite(Sort.by("costPrice"), Product.class).stream()).singleElement()
            .extracting(Sort.Order::getProperty).isEqualTo("costPrice");
    }

    @Test
    void translatesNestedPaths() {
        Sort rewritten = rewriter.rewrite(Sort.by("dims.width_mm"), Product.class);

        assertThat(rewritten.stream()).singleElement()
            .extracting(Sort.Order::getProperty).isEqualTo("dims.width");
    }

    @Test
    void preservesDirectionCaseHandlingAndNullHandlingOfEveryOrder() {
        Sort sort = Sort.by(
            Sort.Order.desc("cost_price").ignoreCase().nullsLast(),
            Sort.Order.asc("name"));

        Sort rewritten = rewriter.rewrite(sort, Product.class);

        assertThat(rewritten.stream()).hasSize(2);
        Sort.Order first = rewritten.iterator().next();
        assertThat(first.getProperty()).isEqualTo("costPrice");
        assertThat(first.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(first.isIgnoreCase()).isTrue();
        assertThat(first.getNullHandling()).isEqualTo(Sort.NullHandling.NULLS_LAST);
        assertThat(rewritten.getOrderFor("name")).isNotNull()
            .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void rejectsUnknownRootProperty() {
        Sort unknown = Sort.by("not_a_field");

        assertThatThrownBy(() -> rewriter.rewrite(unknown, Product.class))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("not_a_field");
    }

    @Test
    void rejectsUnknownNestedPropertyButNamesTheOffendingSegment() {
        Sort unknownNested = Sort.by("dims.depth");

        assertThatThrownBy(() -> rewriter.rewrite(unknownNested, Product.class))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("'depth'")
            .hasMessageContaining("'dims.depth'");
    }

    @Test
    void passesThroughMapKeysAndDollarSegments() {
        // Map keys are dynamic, "$id" is a backend accessor: neither is checked against the bean.
        assertThat(rewriter.rewrite(Sort.by("attributes.color"), Product.class).stream()).singleElement()
            .extracting(Sort.Order::getProperty).isEqualTo("attributes.color");
        assertThat(rewriter.rewrite(Sort.by("dims.$id"), Product.class).stream()).singleElement()
            .extracting(Sort.Order::getProperty).isEqualTo("dims.$id");
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
        @JsonProperty("width_mm")
        private int width;
    }
}
