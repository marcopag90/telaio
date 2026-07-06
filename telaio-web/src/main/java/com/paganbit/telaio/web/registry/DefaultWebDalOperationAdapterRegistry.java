package com.paganbit.telaio.web.registry;

import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.exception.DalNotFoundException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link WebDalOperationAdapterRegistry}. The first registration for a given name wins.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultWebDalOperationAdapterRegistry implements WebDalOperationAdapterRegistry {

    private final ConcurrentHashMap<String, DalOperationAdapter<?, ?>> adapters = new ConcurrentHashMap<>();

    @Override
    public void register(String name, DalOperationAdapter<?, ?> adapter) {
        adapters.putIfAbsent(name, adapter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DalOperationAdapter<Object, Object> get(String name) {
        final var adapter = adapters.get(name);
        if (adapter == null) {
            throw new DalNotFoundException(name);
        }
        return (DalOperationAdapter<Object, Object>) adapter;
    }
}
