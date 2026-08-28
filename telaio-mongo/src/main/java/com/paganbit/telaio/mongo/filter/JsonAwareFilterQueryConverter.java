package com.paganbit.telaio.mongo.filter;

import com.paganbit.telaio.core.json.JsonFieldNameFilterRewriter;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Query;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * A {@link FilterQueryConverter} decorator that makes the Turkraft Spring Filter query language
 * understand Jackson {@code @JsonProperty} wire names.
 *
 * <p>Turkraft resolves filter field references by reflection against the entity class, so a property
 * exposed under a different name with {@code @JsonProperty} (e.g. {@code costPrice} ↔
 * {@code "cost_price"}) is unreachable by the name clients actually use. This decorator translates the
 * JSON names in the parsed filter back to Java property names via {@link JsonFieldNameFilterRewriter}
 * before delegating to the real converter; names that are already Java names (or unknown) pass through
 * untouched, so the behavior is purely additive. String filters are parsed with the framework's
 * {@link FilterStringConverter} first, so the rewrite applies to them as well.</p>
 *
 * <p>The delegate builds {@code $expr}-wrapped queries from typed BSON values (temporal literals become
 * BSON dates, identifiers become {@code ObjectId}s); being {@code $expr}-based, filtered reads generally
 * cannot use indexes.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public class JsonAwareFilterQueryConverter implements FilterQueryConverter {

    private final FilterQueryConverter delegate;

    private final FilterStringConverter filterStringConverter;

    private final JsonFieldNameFilterRewriter rewriter;

    /**
     * Creates the decorator.
     *
     * @param delegate              the converter performing the actual conversion
     * @param filterStringConverter parser for the string form of a filter
     * @param objectMapper          the application mapper, used to introspect {@code @JsonProperty} renames
     */
    public JsonAwareFilterQueryConverter(
        FilterQueryConverter delegate,
        FilterStringConverter filterStringConverter,
        ObjectMapper objectMapper
    ) {
        this.delegate = Objects.requireNonNull(delegate, "FilterQueryConverter delegate must not be null");
        this.filterStringConverter =
            Objects.requireNonNull(filterStringConverter, "FilterStringConverter must not be null");
        this.rewriter = new JsonFieldNameFilterRewriter(new JsonPropertyPathResolver(
            Objects.requireNonNull(objectMapper, "ObjectMapper must not be null")));
    }

    @Override
    public Query convert(String filter, Class<?> entityClass) {
        return convert(filterStringConverter.convert(filter), entityClass);
    }

    @Override
    public Query convert(FilterNode filter, Class<?> entityClass) {
        return delegate.convert(rewriter.rewrite(filter, entityClass), entityClass);
    }

    @Override
    public Document convertToDocument(String filter, Class<?> entityClass) {
        return convertToDocument(filterStringConverter.convert(filter), entityClass);
    }

    @Override
    public Document convertToDocument(FilterNode filter, Class<?> entityClass) {
        return delegate.convertToDocument(rewriter.rewrite(filter, entityClass), entityClass);
    }
}
