package com.paganbit.telaio.mongo.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turkraft.springfilter.helper.FieldTypeResolver;
import com.turkraft.springfilter.helper.JsonNodeHelper;
import com.turkraft.springfilter.helper.JsonNodeHelperImpl;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.transformer.processor.factory.FilterNodeProcessorFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.format.support.DefaultFormattingConversionService;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JsonAwareFilterQueryConverter}: JSON wire names are rewritten to Java
 * property names <em>before</em> Turkraft's transformer runs, {@code @Id} fields map to the raw
 * {@code $_id}, and the produced query is {@code $expr}-wrapped. Field-only filter trees keep the
 * transformer away from the (mocked) processor factories, so no server or Spring context is needed;
 * the full parse-to-match chain runs in {@code JsonAwareFilterQueryConverterIT}.
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

    private final FieldTypeResolver fieldTypeResolver = mock(FieldTypeResolver.class);

    private final FilterNodeProcessorFactories processorFactories = mock(FilterNodeProcessorFactories.class);

    private final JsonNodeHelper jsonNodeHelper =
        new JsonNodeHelperImpl(JsonMapper.builder().build(), fieldTypeResolver);

    private final JsonAwareFilterQueryConverter converter = new JsonAwareFilterQueryConverter(
        new DefaultFormattingConversionService(),
        processorFactories,
        fieldTypeResolver,
        jsonNodeHelper,
        JsonMapper.builder().build()
    );

    @Test
    void convert_rewritesJsonWireNameToJavaPropertyName() {
        Query query = converter.convert(new FieldNode("cost_price"), Widget.class);

        assertThat(query.getQueryObject()).containsEntry("$expr", "$costPrice");
    }

    @Test
    void convert_leavesJavaPropertyNameUntouched() {
        Query query = converter.convert(new FieldNode("costPrice"), Widget.class);

        assertThat(query.getQueryObject()).containsEntry("$expr", "$costPrice");
    }

    @Test
    void convert_leavesUnknownFieldNameUntouched() {
        Query query = converter.convert(new FieldNode("unknown"), Widget.class);

        assertThat(query.getQueryObject()).containsEntry("$expr", "$unknown");
    }

    @Test
    void convert_mapsIdAnnotatedFieldToRawIdField() throws NoSuchFieldException {
        when(fieldTypeResolver.getField(Widget.class, "id")).thenReturn(Widget.class.getDeclaredField("id"));

        Query query = converter.convert(new FieldNode("id"), Widget.class);

        assertThat(query.getQueryObject()).containsEntry("$expr", "$_id");
    }

    @Test
    void convert_wrapsResultWithMongoExpression() {
        Query query = converter.convert(new FieldNode("name"), Widget.class);

        assertThat(query.getQueryObject()).containsOnlyKeys("$expr");
    }
}
