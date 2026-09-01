package com.paganbit.telaio.core.json;

import com.paganbit.telaio.core.exception.DalInvalidSortException;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rewrites a {@link Sort}, replacing every <em>JSON</em> wire name with the underlying <em>Java</em>
 * property name. Each {@link Sort.Order} is translated via
 * {@link JsonPropertyPathResolver#resolveJavaPath}; direction, case-sensitivity and null handling
 * are preserved.
 *
 * <p>Property paths are checked strictly: a property the entity does not expose is rejected with a
 * {@link DalInvalidSortException}. Both the JSON name and the Java name of a property are accepted —
 * e.g. {@code cost_price} and {@code costPrice} — so a sort already written with Java names keeps
 * working unchanged.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public class JsonFieldNameSortRewriter {

    private final JsonPropertyPathResolver pathResolver;

    public JsonFieldNameSortRewriter(JsonPropertyPathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    /**
     * Returns a copy of {@code sort} with every order's property translated from its JSON wire name to
     * the corresponding Java property name on {@code entityType}.
     *
     * @param sort       the caller-supplied sort
     * @param entityType the root entity type the property names are resolved against
     * @return an equivalent sort with property names rewritten to Java property names
     * @throws DalInvalidSortException if an order references a property the entity does not expose
     */
    public Sort rewrite(Sort sort, Class<?> entityType) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            final var resolveJavaPath = pathResolver.resolveJavaPath(entityType, order.getProperty());
            if (!resolveJavaPath.resolved()) {
                throw DalInvalidSortException.unknownProperty(
                    Objects.requireNonNull(resolveJavaPath.unresolvedSegment()), order.getProperty());
            }
            String javaPath = resolveJavaPath.javaPath();
            orders.add(javaPath.equals(order.getProperty()) ? order : order.withProperty(javaPath));
        }
        return Sort.by(orders);
    }
}
