package com.paganbit.telaio.mongo.filter;

import com.turkraft.springfilter.parser.node.FilterNode;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Converts a parsed Turkraft {@link FilterNode} into a Spring Data Mongo {@link Query}.
 *
 * <p>The entity type travels as an explicit argument because Turkraft's transformer is stateful and
 * per-entity-type. Applications may replace the default {@link JsonAwareFilterQueryConverter} by
 * declaring their own bean.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public interface FilterQueryConverter {

    /**
     * Converts the given filter tree into a Mongo {@link Query} targeting the given entity type.
     *
     * @param node       the parsed filter tree
     * @param entityType the root entity type the field names are resolved against
     * @return the equivalent Mongo query
     */
    Query convert(FilterNode node, Class<?> entityType);
}
