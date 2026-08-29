package com.paganbit.telaio.mongo.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
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
    void convert_rejectsUnknownFieldName() {
        // A field the entity does not expose is a client fault, rejected before the delegate is asked.
        FieldNode unknown = new FieldNode("unknown");

        assertThatThrownBy(() -> converter.convert(unknown, Widget.class))
            .isInstanceOf(DalInvalidFilterException.class)
            .hasMessageContaining("unknown");
        verify(delegate, never()).convert(any(FilterNode.class), any());
    }

    @Test
    void convert_wrapsOnlyUnsupportedFunctionsAsInvalidFilter() {
        FieldNode node = new FieldNode("cost_price");

        // A function the backend has no processor for is a client fault (both overloads)...
        UnsupportedOperationException unsupported = new UnsupportedOperationException("No transformer");
        doThrow(unsupported).when(delegate).convert(any(FilterNode.class), eq(Widget.class));
        assertThatThrownBy(() -> converter.convert(node, Widget.class))
            .isInstanceOf(DalInvalidFilterException.class)
            .hasCause(unsupported);
        doThrow(unsupported).when(delegate).convertToDocument(any(FilterNode.class), eq(Widget.class));
        assertThatThrownBy(() -> converter.convertToDocument(node, Widget.class))
            .isInstanceOf(DalInvalidFilterException.class)
            .hasCause(unsupported);

        // ...whereas a literal that does not convert propagates unchanged (a server fault, consistently
        // with the other backends) — e.g. new ObjectId("zzz") inside the BSON transformer.
        ConversionFailedException conversion = new ConversionFailedException(
            TypeDescriptor.valueOf(String.class), TypeDescriptor.valueOf(BigDecimal.class), "abc",
            new NumberFormatException("abc"));
        doThrow(conversion).when(delegate).convert(any(FilterNode.class), eq(Widget.class));
        assertThatThrownBy(() -> converter.convert(node, Widget.class)).isSameAs(conversion);

        IllegalArgumentException argument = new IllegalArgumentException("invalid hexadecimal representation");
        doThrow(argument).when(delegate).convertToDocument(any(FilterNode.class), eq(Widget.class));
        assertThatThrownBy(() -> converter.convertToDocument(node, Widget.class)).isSameAs(argument);
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
