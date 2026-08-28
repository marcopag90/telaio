package com.paganbit.telaio.mongo.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JsonAwareFilterQueryConverter}: the decorator rewrites {@code @JsonProperty}
 * wire names to Java property names and hands the rewritten tree to the delegate; the string overloads
 * are parsed first. The full parse-to-match chain runs in
 * {@code JsonAwareFilterQueryConverterIntegrationTest}.
 */
class JsonAwareFilterQueryConverterTest {

    @SuppressWarnings("unused")
    static class Widget {

        @Id
        private String id;

        @JsonProperty("cost_price")
        private BigDecimal costPrice;

        @Nullable
        private String name;
    }

    private final FilterQueryConverter delegate = mock(FilterQueryConverter.class);

    private final FilterStringConverter filterStringConverter = mock(FilterStringConverter.class);

    private final Query delegateQuery = new BasicQuery(new Document("$expr", "$costPrice"));

    private final Document delegateDocument = new Document("$expr", "$costPrice");

    private final JsonAwareFilterQueryConverter converter = new JsonAwareFilterQueryConverter(
        delegate, filterStringConverter, JsonMapper.builder().build());

    private String fieldNamePassedToDelegate() {
        ArgumentCaptor<FilterNode> captor = ArgumentCaptor.forClass(FilterNode.class);
        verify(delegate).convert(captor.capture(), eq(Widget.class));
        return ((FieldNode) captor.getValue()).getName();
    }

    @Test
    void convert_rewritesJsonWireNameToJavaPropertyName() {
        doReturn(delegateQuery).when(delegate).convert(any(FilterNode.class), eq(Widget.class));

        Query query = converter.convert(new FieldNode("cost_price"), Widget.class);

        assertThat(query).isSameAs(delegateQuery);
        assertThat(fieldNamePassedToDelegate()).isEqualTo("costPrice");
    }

    @Test
    void convert_leavesJavaPropertyNameUntouched() {
        doReturn(delegateQuery).when(delegate).convert(any(FilterNode.class), eq(Widget.class));

        converter.convert(new FieldNode("costPrice"), Widget.class);

        assertThat(fieldNamePassedToDelegate()).isEqualTo("costPrice");
    }

    @Test
    void convert_leavesUnknownFieldNameUntouched() {
        doReturn(delegateQuery).when(delegate).convert(any(FilterNode.class), eq(Widget.class));

        converter.convert(new FieldNode("unknown"), Widget.class);

        assertThat(fieldNamePassedToDelegate()).isEqualTo("unknown");
    }

    @Test
    void convert_parsesStringFiltersBeforeRewriting() {
        doReturn(new FieldNode("cost_price")).when(filterStringConverter).convert("cost_price");
        doReturn(delegateQuery).when(delegate).convert(any(FilterNode.class), eq(Widget.class));

        Query query = converter.convert("cost_price", Widget.class);

        assertThat(query).isSameAs(delegateQuery);
        assertThat(fieldNamePassedToDelegate()).isEqualTo("costPrice");
    }

    @Test
    void convertToDocument_rewritesAndDelegatesForBothOverloads() {
        doReturn(new FieldNode("cost_price")).when(filterStringConverter).convert("cost_price");
        doReturn(delegateDocument).when(delegate).convertToDocument(any(FilterNode.class), eq(Widget.class));

        assertThat(converter.convertToDocument(new FieldNode("cost_price"), Widget.class))
            .isSameAs(delegateDocument);
        assertThat(converter.convertToDocument("cost_price", Widget.class)).isSameAs(delegateDocument);

        ArgumentCaptor<FilterNode> captor = ArgumentCaptor.forClass(FilterNode.class);
        verify(delegate, times(2)).convertToDocument(captor.capture(), eq(Widget.class));
        assertThat(captor.getAllValues())
            .allSatisfy(node -> assertThat(((FieldNode) node).getName()).isEqualTo("costPrice"));
    }

    @Test
    void constructor_rejectsNullCollaborators() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertThatNullPointerException()
            .isThrownBy(() -> new JsonAwareFilterQueryConverter(null, filterStringConverter, mapper))
            .withMessageContaining("delegate");
        assertThatNullPointerException()
            .isThrownBy(() -> new JsonAwareFilterQueryConverter(delegate, null, mapper))
            .withMessageContaining("FilterStringConverter");
        assertThatNullPointerException()
            .isThrownBy(() -> new JsonAwareFilterQueryConverter(delegate, filterStringConverter, null))
            .withMessageContaining("ObjectMapper");
    }
}
