package io.paganbit.telaio.introspection;

import org.springframework.util.StringUtils;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the name of a property based on a method reference.
 * This utility is used to replace hardcoded string-based field names with refactor-safe alternatives.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public final class PropertyNameResolver {

    /**
     * Memoizes resolved property names keyed by the synthetic lambda class produced at each
     * method-reference call site. A given lambda class always resolves to the same property
     * name, so the cache is correct, and its size is bounded by the number of distinct
     * {@code propertyName(...)} call sites in the code (a compile-time constant) rather than by
     * the number of invocations.
     */
    private static final Map<Class<?>, String> CACHE = new ConcurrentHashMap<>();

    private PropertyNameResolver() {
    }

    /**
     * Resolves the property name from a method reference.
     * <p>
     * The result is memoized per lambda class (the synthetic class generated for the
     * method-reference call site): the first call performs the reflective lambda resolution,
     * later calls for the same call site return the cached name in O(1).
     * </p>
     *
     * @param ref the method reference to resolve
     * @param <T> the input type
     * @param <R> the return type
     * @return the name of the property
     */
    public static <T, R> String propertyName(PropertyRef<T, R> ref) {
        return CACHE.computeIfAbsent(ref.getClass(), c -> resolve(ref));
    }

    @SuppressWarnings("squid:S3011")
    private static <T, R> String resolve(PropertyRef<T, R> ref) {
        try {
            Method method = ref.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            Object serializedForm = method.invoke(ref);
            if (serializedForm instanceof SerializedLambda lambda) {
                String methodName = lambda.getImplMethodName();
                if (methodName.startsWith("get")) {
                    return StringUtils.uncapitalize(methodName.substring(3));
                } else if (methodName.startsWith("is")) {
                    return StringUtils.uncapitalize(methodName.substring(2));
                } else {
                    return methodName;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve property name", e);
        }
        throw new IllegalArgumentException("Not a valid method reference");
    }

    /**
     * Builds a nested property path by combining dot-separated segments.
     *
     * @param first  the first property
     * @param nested additional nested properties
     * @return the full property path
     */
    public static String propertyPath(String first, String... nested) {
        StringBuilder builder = new StringBuilder(first);
        for (String segment : nested) {
            builder.append('.').append(segment);
        }
        return builder.toString();
    }

    /**
     * Utility builder to construct a property path from chained method references.
     */
    public static class PropertyPathBuilder<T> {

        private final List<String> segments = new ArrayList<>();

        private PropertyPathBuilder(PropertyRef<T, ?> ref) {
            segments.add(propertyName(ref));
        }

        private PropertyPathBuilder(List<String> existing) {
            segments.addAll(existing);
        }

        public static <T> PropertyPathBuilder<T> of(PropertyRef<T, ?> ref) {
            return new PropertyPathBuilder<>(ref);
        }

        public <R> PropertyPathBuilder<R> then(PropertyRef<R, ?> ref) {
            segments.add(propertyName(ref));
            return new PropertyPathBuilder<>(segments);
        }

        public String build() {
            return String.join(".", segments);
        }
    }
}
