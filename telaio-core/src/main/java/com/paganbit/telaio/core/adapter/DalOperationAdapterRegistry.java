package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.exception.DalNotFoundException;

/**
 * Holds the assembled {@link DalOperationAdapter} of each DAL, indexed by name.
 *
 * <p>Populated at startup by the {@link DalOperationAdapterAssembler}; every remote boundary resolves the
 * adapter of a DAL from here instead of reaching the {@link com.paganbit.telaio.core.Dal} bean directly,
 * so the cross-cutting interceptors woven around it always apply. Per-operation exposure is one of those
 * interceptors, contributed by the boundary module that enforces it; the registry itself does not
 * enforce it.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public interface DalOperationAdapterRegistry {

    /**
     * Registers a pre-built adapter for the given DAL name.
     *
     * @param name    the DAL name
     * @param adapter the adapter to register
     */
    void register(String name, DalOperationAdapter<?, ?> adapter);

    /**
     * Retrieves the adapter registered under the given DAL name.
     *
     * @param name the DAL name
     * @return the registered adapter
     * @throws DalNotFoundException if no adapter is registered for the given name
     */
    DalOperationAdapter<Object, Object> get(String name);
}
