package com.paganbit.telaio.mongo.filter;

import com.paganbit.telaio.core.json.JsonFieldNameFilterRewriter;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.turkraft.springfilter.helper.FieldTypeResolver;
import com.turkraft.springfilter.helper.JsonNodeHelper;
import com.turkraft.springfilter.parser.node.FilterNode;
import com.turkraft.springfilter.transformer.FilterJsonNodeTransformer;
import com.turkraft.springfilter.transformer.processor.factory.FilterNodeProcessorFactories;
import org.bson.Document;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Default {@link FilterQueryConverter}: rewrites JSON wire names to Java property names, then runs
 * Turkraft's {@code FilterJsonNodeTransformer} and wraps the result into an {@code $expr} query.
 *
 * <p>Turkraft resolves field names by reflection against the entity class, so a
 * {@code @JsonProperty}-renamed field would not resolve — the {@link FilterNode} tree is rewritten
 * via {@link JsonFieldNameFilterRewriter} before transformation; unknown names pass through
 * untouched. Being {@code $expr}-based, filtered reads generally cannot use indexes.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public class JsonAwareFilterQueryConverter implements FilterQueryConverter {

    private final ConversionService conversionService;

    private final FilterNodeProcessorFactories processorFactories;

    private final FieldTypeResolver fieldTypeResolver;

    private final JsonNodeHelper jsonNodeHelper;

    private final JsonFieldNameFilterRewriter rewriter;

    private final ObjectMapper objectMapper;

    public JsonAwareFilterQueryConverter(
        ConversionService conversionService,
        FilterNodeProcessorFactories processorFactories,
        FieldTypeResolver fieldTypeResolver,
        JsonNodeHelper jsonNodeHelper,
        ObjectMapper objectMapper
    ) {
        this.conversionService = conversionService;
        this.processorFactories = processorFactories;
        this.fieldTypeResolver = fieldTypeResolver;
        this.jsonNodeHelper = jsonNodeHelper;
        this.objectMapper = objectMapper;
        this.rewriter = new JsonFieldNameFilterRewriter(new JsonPropertyPathResolver(objectMapper));
    }

    @Override
    public Query convert(FilterNode node, Class<?> entityType) {
        FilterNode rewritten = rewriter.rewrite(node, entityType);
        // The transformer is stateful (entity type + per-node target types): one instance per call.
        final var transformer = new FilterJsonNodeTransformer(
            conversionService, objectMapper, processorFactories, fieldTypeResolver, entityType);
        ObjectNode json = jsonNodeHelper.wrapWithMongoExpression(transformer.transform(rewritten));
        // TODO(roadmap): Document.parse serializes temporal filter values as plain strings, so
        //  comparisons against BSON date fields do not match — upstream Turkraft mongo limitation.
        //  See docs/roadmap.md.
        return new BasicQuery(Document.parse(json.toString()));
    }
}
