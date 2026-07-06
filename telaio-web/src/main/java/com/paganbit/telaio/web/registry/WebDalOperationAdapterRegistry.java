package com.paganbit.telaio.web.registry;

import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.exception.DalNotFoundException;

/**
 * Holds the assembled {@link DalOperationAdapter} of each DAL, indexed by name.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface WebDalOperationAdapterRegistry {

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
