package com.paganbit.telaio.introspection;

import java.util.Collection;

/**
 * Contribution of additional simple types to the {@link DefaultSimpleTypePredicate}.
 *
 * <p>Modules introducing backend-specific types expose an implementation as a bean; the framework
 * aggregates every contribution into the shared predicate instance. This keeps the classification
 * type-safe while this module stays free of backend dependencies. A type classified as simple
 * travels raw in the {@code {id}} path segment, so contributed types must (de)serialize to a
 * plain string.</p>
 *
 * <p>An application defining its own {@link DefaultSimpleTypePredicate} bean replaces the
 * aggregation entirely: contributions are then no longer applied and must be re-included in the
 * replacement explicitly.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
@FunctionalInterface
public interface SimpleTypeContributor {

    /**
     * Returns the types to classify as simple, matched by direct equality.
     *
     * @return the contributed simple types
     */
    Collection<Class<?>> simpleTypes();
}
