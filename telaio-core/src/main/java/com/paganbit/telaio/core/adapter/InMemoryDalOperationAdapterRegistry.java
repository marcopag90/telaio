package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.exception.DalNotFoundException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link DalOperationAdapterRegistry}. The first registration for a given name wins.
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public class InMemoryDalOperationAdapterRegistry implements DalOperationAdapterRegistry {

    private final ConcurrentHashMap<String, DalOperationAdapter<?>> adapters = new ConcurrentHashMap<>();

    @Override
    public void register(String name, DalOperationAdapter<?> adapter) {
        adapters.putIfAbsent(name, adapter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DalOperationAdapter<Object> get(String name) {
        final var adapter = adapters.get(name);
        if (adapter == null) {
            throw new DalNotFoundException(name);
        }
        return (DalOperationAdapter<Object>) adapter;
    }
}
