package com.paganbit.telaio.introspection;

import org.jspecify.annotations.Nullable;

import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Predicate;

/**
 * A predicate implementation that determines whether a given class represents a simple type.
 *
 * <p>Beyond the built-in classification, additional simple types can be contributed at
 * construction time — typically aggregated from {@link SimpleTypeContributor} implementations, so
 * that modules introducing backend-specific types (e.g. a persistence backend's identifier type)
 * extend the classification without this module gaining any dependency on them.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultSimpleTypePredicate implements Predicate<Class<?>> {

    /**
     * Set of base Java types that are considered simple types.
     * These types are checked for direct equality with the class being tested.
     */
    private static final Set<Class<?>> BASE_TYPES = Set.of(
        Boolean.class,
        Character.class,
        String.class,
        UUID.class,
        Optional.class
    );

    /**
     * Set of Java types that are considered simple if the class being tested
     * is assignable from any of these types (is a subclass or implements the interface).
     */
    private static final Set<Class<?>> ASSIGNABLE_TYPES = Set.of(
        Number.class,
        Enum.class,
        Date.class,
        Temporal.class,
        Collection.class,
        Map.class
    );

    /**
     * Set of additional types contributed at construction time, checked for direct equality with
     * the class being tested.
     */
    private final Set<Class<?>> contributedTypes;

    /**
     * Creates a predicate recognizing only the built-in simple types.
     */
    public DefaultSimpleTypePredicate() {
        this(Set.of());
    }

    /**
     * Creates a predicate recognizing the built-in simple types plus the given contributed ones.
     *
     * @param contributedTypes additional types to classify as simple, matched by direct equality
     */
    public DefaultSimpleTypePredicate(Collection<Class<?>> contributedTypes) {
        this.contributedTypes = Set.copyOf(contributedTypes);
    }

    /**
     * Tests whether the given class represents a simple type.
     *
     * @param clazz The class to test
     * @return true if the class represents a simple type, false otherwise
     */
    @Override
    public boolean test(@Nullable Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        if (clazz.isArray() || clazz.isPrimitive() || clazz.isEnum() || BASE_TYPES.contains(clazz)
            || contributedTypes.contains(clazz)) {
            return true;
        }
        for (Class<?> assignableType : ASSIGNABLE_TYPES) {
            if (assignableType.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }
}
