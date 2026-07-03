package io.paganbit.telaio.introspection;

import org.jspecify.annotations.Nullable;

import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Predicate;

/**
 * A predicate implementation that determines whether a given class represents a simple type.
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
        if (clazz.isArray() || clazz.isPrimitive() || clazz.isEnum() || BASE_TYPES.contains(clazz)) {
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
