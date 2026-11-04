package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.exception.DalNotFoundException;

/**
 * Holds the assembled {@link DalOperationAdapter} of each (channel, DAL) pair.
 *
 * <p>Populated at startup by the {@link DalOperationAdapterAssembler}: one chain per DAL for every
 * channel that registered a {@link com.paganbit.telaio.core.channel.DalChannelDefinition}, so a context
 * without channel modules holds nothing. A channel resolves the adapter of a DAL from here, under its
 * own channel, instead of reaching the {@link com.paganbit.telaio.core.Dal} bean directly, so the
 * cross-cutting interceptors woven around it always apply — per-operation exposure, enforced by core
 * from the channel definition's answer, among them. A DAL the channel definition exposes nothing of has
 * no chain on that channel, exactly like an internal DAL.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public interface DalOperationAdapterRegistry {

    /**
     * Registers a pre-built adapter for the given channel and DAL name.
     *
     * @param channel the channel the adapter was assembled for
     * @param name    the DAL name
     * @param adapter the adapter to register
     */
    void register(DalChannel channel, String name, DalOperationAdapter<?> adapter);

    /**
     * Retrieves the adapter registered for the given channel and DAL name.
     *
     * @param channel the channel
     * @param name    the DAL name
     * @return the registered adapter
     * @throws DalNotFoundException if no adapter is registered for the given channel and name
     */
    DalOperationAdapter<Object> get(DalChannel channel, String name);
}
