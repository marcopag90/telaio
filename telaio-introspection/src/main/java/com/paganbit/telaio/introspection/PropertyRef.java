package com.paganbit.telaio.introspection;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Serializable functional reference to a property accessor.
 * <p>
 * Typically used with method references such as {@code User::getEmail} to
 * capture property metadata while retaining the ability to invoke the accessor
 * as a standard {@link Function}.
 * </p>
 *
 * @param <T> the source type that declares the property
 * @param <R> the property value type returned by the accessor
 * @author Marco Pagan
 * @since 1.0.0
 */
@FunctionalInterface
public interface PropertyRef<T, R> extends Function<T, R>, Serializable {
}
