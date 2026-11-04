package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.exception.DalNotFoundException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link DalOperationAdapterRegistry}. The first registration for a given (channel, name) wins.
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public class InMemoryDalOperationAdapterRegistry implements DalOperationAdapterRegistry {

    private record Key(DalChannel channel, String name) {
    }

    private final ConcurrentHashMap<Key, DalOperationAdapter<?>> adapters = new ConcurrentHashMap<>();

    @Override
    public void register(DalChannel channel, String name, DalOperationAdapter<?> adapter) {
        adapters.putIfAbsent(new Key(channel, name), adapter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DalOperationAdapter<Object> get(DalChannel channel, String name) {
        final var adapter = adapters.get(new Key(channel, name));
        if (adapter == null) {
            throw new DalNotFoundException(name);
        }
        return (DalOperationAdapter<Object>) adapter;
    }
}
