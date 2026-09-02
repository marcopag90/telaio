package com.paganbit.telaio.jpa.filter;

import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.paganbit.telaio.core.json.JsonFieldNameFilterRewriter;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.turkraft.springfilter.converter.FilterSpecification;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;

import java.util.Objects;
import java.util.Set;

/**
 * A {@link FilterSpecificationConverter} decorator that makes the Turkraft Spring Filter query language
 * understand Jackson {@code @JsonProperty} wire names.
 *
 * <p>Turkraft resolves filter field references straight against the JPA metamodel, so a property exposed
 * under a different name with {@code @JsonProperty} (e.g. {@code costPrice} ↔ {@code "cost_price"}) is
 * unreachable by the name clients actually use — {@code q=cost_price>100} fails with
 * <em>Unable to locate Attribute [cost_price]</em>. This decorator translates the JSON names in the
 * parsed filter back to Java property names before delegating to the real converter.</p>
 *
 * <p>The rewrite is performed lazily inside {@link FilterSpecification#toPredicate}, where the entity
 * type is known from {@link Root#getJavaType()} — the single-argument {@code convert(FilterNode)} carries
 * no type information on its own. Field names that are already Java names pass through untouched (see
 * {@link JsonFieldNameFilterRewriter}), so queries that worked before keep working and renamed fields now
 * work too.</p>
 *
 * <p>A filter that cannot be applied because of its <em>shape</em> — an unknown field (rejected by the
 * rewriter), a field the wire exposes but the persistence unit does not map (rejected by
 * {@link JpaFilterFieldValidator} against the metamodel), or a function this backend has no processor
 * for — surfaces as a {@link DalInvalidFilterException}, a client fault.
 * A literal that does not convert to the field's type
 * is deliberately <em>not</em> intercepted: the persistence layer reports it (as a data-access failure)
 * exactly as it would for any other query, and the same holds on every backend.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class JsonAwareFilterSpecificationConverter implements FilterSpecificationConverter {

    private final FilterSpecificationConverter delegate;
    private final JsonFieldNameFilterRewriter rewriter;

    /**
     * Creates the decorator.
     *
     * @param delegate     the converter performing the actual conversion
     * @param pathResolver the path resolver, used to translate {@code @JsonProperty} renames
     */
    public JsonAwareFilterSpecificationConverter(
        FilterSpecificationConverter delegate,
        JsonPropertyPathResolver pathResolver
    ) {
        this.delegate = Objects.requireNonNull(delegate, "FilterSpecificationConverter delegate must not be null");
        this.rewriter = new JsonFieldNameFilterRewriter(
            Objects.requireNonNull(pathResolver, "JsonPropertyPathResolver must not be null"));
    }

    @Override
    public <T> FilterSpecification<T> convert(String query) {
        return wrap(delegate.<T>convert(query).getFilter());
    }

    @Override
    public <T> FilterSpecification<T> convert(FilterNode node) {
        return wrap(node);
    }

    @Override
    public String convert(FilterSpecification<?> specification) {
        return delegate.convert(specification);
    }

    @Override
    @SuppressWarnings("squid:S2638")
    public @Nullable Set<ConvertiblePair> getConvertibleTypes() {
        return delegate.getConvertibleTypes();
    }

    @Override
    public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        if (source instanceof String query) {
            return convert(query);
        }
        if (source instanceof FilterNode node) {
            return convert(node);
        }
        if (source instanceof FilterSpecification<?> specification) {
            return convert(specification);
        }
        return delegate.convert(source, sourceType, targetType);
    }

    /**
     * Wraps a parsed filter in a {@link FilterSpecification} that rewrites JSON field names to Java
     * property names against the query's root entity type, then delegates predicate building.
     */
    private <T> FilterSpecification<T> wrap(FilterNode node) {
        return new FilterSpecification<>() {

            @Override
            public FilterNode getFilter() {
                return node;
            }

            @Override
            public @Nullable Predicate toPredicate(
                Root<T> root,
                CriteriaQuery<?> query,
                CriteriaBuilder criteriaBuilder
            ) {
                FilterNode filterNode = rewriter.rewrite(node, root.getJavaType());
                JpaFilterFieldValidator.validate(filterNode, root.getModel());
                try {
                    return delegate.<T>convert(filterNode).toPredicate(root, query, criteriaBuilder);
                } catch (UnsupportedOperationException e) {
                    throw new DalInvalidFilterException("Invalid filter expression", e);
                }
            }
        };
    }
}
