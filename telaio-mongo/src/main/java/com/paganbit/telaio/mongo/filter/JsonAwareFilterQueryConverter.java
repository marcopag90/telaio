package com.paganbit.telaio.mongo.filter;

import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.paganbit.telaio.core.json.JsonFieldNameFilterRewriter;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Query;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A {@link FilterQueryConverter} decorator that makes the Turkraft Spring Filter query language
 * understand Jackson {@code @JsonProperty} wire names.
 *
 * <p>Turkraft resolves filter field references by reflection against the entity class, so a property
 * exposed under a different name with {@code @JsonProperty} (e.g. {@code costPrice} ↔
 * {@code "cost_price"}) is unreachable by the name clients actually use. This decorator translates the
 * JSON names in the parsed filter back to Java property names via {@link JsonFieldNameFilterRewriter}
 * before delegating to the real converter; names that are already Java names pass through untouched.
 * String filters are parsed with the framework's {@link FilterStringConverter} first, so the rewrite
 * applies to them as well.</p>
 *
 * <p>A filter that cannot be applied because of its <em>shape</em> — an unknown field (rejected by the
 * rewriter) or a function this backend has no processor for — surfaces as a
 * {@link DalInvalidFilterException}, a client fault. A literal that does not convert to the field's type
 * (e.g. a malformed {@code ObjectId} or {@code UUID}) is deliberately <em>not</em> intercepted: the
 * conversion failure propagates as a data-access failure, exactly as on every other backend.</p>
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
        FilterNode rewritten = rewriter.rewrite(filter, entityClass);
        return guarded(() -> delegate.convert(rewritten, entityClass));
    }

    @Override
    public Document convertToDocument(String filter, Class<?> entityClass) {
        return convertToDocument(filterStringConverter.convert(filter), entityClass);
    }

    @Override
    public Document convertToDocument(FilterNode filter, Class<?> entityClass) {
        FilterNode rewritten = rewriter.rewrite(filter, entityClass);
        return guarded(() -> delegate.convertToDocument(rewritten, entityClass));
    }

    /**
     * Runs the delegate conversion, translating "no processor for this function" into a
     * {@link DalInvalidFilterException}; every other failure propagates unchanged.
     */
    private static <R> R guarded(Supplier<R> conversion) {
        try {
            return conversion.get();
        } catch (UnsupportedOperationException e) {
            throw new DalInvalidFilterException("Invalid filter expression", e);
        }
    }
}
