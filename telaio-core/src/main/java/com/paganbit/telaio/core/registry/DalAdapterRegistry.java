package com.paganbit.telaio.core.registry;

/**
 * Adapter registry interface for dynamically resolving adapters by their class type.
 *
 * <p>Implementations of this interface are responsible for locating the appropriate Spring beans
 * that implement the requested adapter classes at runtime, supporting dynamic configuration
 * and modularization of DAL behaviors.</p>
 *
 * <p>If multiple adapter beans are present in the context, the implementation must ensure that
 * the exact adapter class specified is retrieved to avoid ambiguities. If no matching bean is found,
 * an {@link IllegalStateException} should be thrown.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalAdapterRegistry {

    /**
     * Resolves and returns an adapter instance for the requested adapter type.
     * <p>
     * Implementations typically delegate to the Spring application context and
     * are expected to enforce deterministic lookup semantics for the provided
     * class.
     * </p>
     * <p>
     * The returned instance must be assignable to the requested adapter class.
     * If no matching adapter is available, implementations should fail fast with
     * an exception rather than returning {@code null}.
     * </p>
     *
     * @param <T>          the adapter contract type
     * @param adapterClass the adapter class to resolve; must not be {@code null}
     * @return the adapter instance matching {@code adapterClass}
     * @throws IllegalStateException if no matching adapter can be resolved
     */
    <T> T getAdapter(Class<? extends T> adapterClass);
}
