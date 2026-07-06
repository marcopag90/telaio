package com.paganbit.telaio.introspection;

import org.jspecify.annotations.Nullable;

/**
 * Utility class providing methods for type checking.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public final class TypeUtil {

    private static final DefaultSimpleTypePredicate simpleTypePredicate = new DefaultSimpleTypePredicate();

    private TypeUtil() {
        //noop
    }

    /**
     * Determines if the given class represents a complex type.
     * A complex type is any type not considered a simple type by the
     * {@link DefaultSimpleTypePredicate}.
     *
     * @param clazz The class to check
     * @return true if the class represents a complex type, false if it's a simple type, or null
     */
    public static boolean isComplexType(@Nullable Class<?> clazz) {
        return clazz != null && !simpleTypePredicate.test(clazz);
    }
}
