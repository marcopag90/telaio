package com.paganbit.telaio.core.registry;

import com.paganbit.telaio.core.Dal;

/**
 * Identifies a registered DAL service.
 *
 * <p>The entry carries the DAL's identity only: which DAL it is and whether it is available on any
 * channel at all. Which operations it exposes on a given channel is declared to that channel's
 * {@link com.paganbit.telaio.core.channel.DalChannelDefinition}, not here.</p>
 *
 * @param name     the unique registration name of the DAL
 * @param dalClass the concrete {@link Dal} implementation class
 * @param internal whether the DAL is for internal use only and must not be exposed on any channel
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalDefinitionEntry(
    String name,
    Class<? extends Dal<?, ?>> dalClass,
    boolean internal
) {

    /**
     * Creates an exposed (non-internal) definition.
     *
     * @param name     the unique registration name of the DAL
     * @param dalClass the concrete {@link Dal} implementation class
     */
    public DalDefinitionEntry(String name, Class<? extends Dal<?, ?>> dalClass) {
        this(name, dalClass, false);
    }
}
