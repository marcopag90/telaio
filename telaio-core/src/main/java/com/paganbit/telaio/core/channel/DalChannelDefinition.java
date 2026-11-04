package com.paganbit.telaio.core.channel;

import com.paganbit.telaio.core.adapter.DalOperationType;

import java.util.Set;

/**
 * Declares an invocation channel and the CRUD operations each DAL exposes on it.
 *
 * <p>A channel module registers exactly one bean of this type. Core builds one operation adapter chain
 * per (channel, DAL) for every definition present, asks the definition which operations the DAL exposes,
 * and enforces that answer with its own exposure interceptor; the module only translates the resulting
 * {@link com.paganbit.telaio.core.exception.DalOperationNotExposedException} into its own failure
 * vocabulary.</p>
 *
 * <p>Exposure is opt-out and declared per channel, typically through an annotation the channel module
 * owns and stacks on a {@link com.paganbit.telaio.core.annotation.DalService} class: no declaration
 * means every operation, an explicit declaration means only the listed ones, and an empty set means the
 * DAL is not on that channel at all. A DAL marked {@code internal} is never asked: it is on no channel.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public interface DalChannelDefinition {

    /**
     * Returns the channel this definition describes.
     *
     * @return the channel
     */
    DalChannel channel();

    /**
     * Returns the operations the given DAL exposes on this channel.
     *
     * @param dalClass the user-declared class of the {@link com.paganbit.telaio.core.Dal} bean (never a
     *                 proxy class)
     * @return the exposed operations; empty when the DAL is not on this channel
     */
    Set<DalOperationType> exposedOperations(Class<?> dalClass);
}
