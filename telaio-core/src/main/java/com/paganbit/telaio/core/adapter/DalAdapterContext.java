package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.registry.DalCatalog;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;

/**
 * Supplies a {@link DalAdapterInterceptorProvider} with the details of the adapter chain being assembled:
 * the DAL, the channel the chain belongs to, and a read-only view of the registry.
 *
 * <p>Create instances with {@link #of(DalDefinitionEntry, DalChannel, DalCatalog)}: the canonical
 * constructor is an internal detail, so a component added later is a new record component behind the
 * factory, not a signature change for the SPI.</p>
 *
 * @param definition the identity of the DAL being assembled
 * @param channel    the channel the chain is assembled for
 * @param catalog    the registry, for resolving services and definitions
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalAdapterContext(DalDefinitionEntry definition, DalChannel channel, DalCatalog catalog) {

    /**
     * Creates the context of one (channel, DAL) chain.
     *
     * @param definition the identity of the DAL being assembled
     * @param channel    the channel the chain is assembled for
     * @param catalog    the registry, for resolving services and definitions
     * @return the context
     * @since 3.0.0
     */
    public static DalAdapterContext of(DalDefinitionEntry definition, DalChannel channel, DalCatalog catalog) {
        return new DalAdapterContext(definition, channel, catalog);
    }

    /**
     * The registration name of the DAL.
     *
     * @since 3.0.0
     */
    public String dalName() {
        return definition.name();
    }

    /**
     * The user-declared class of the {@link Dal} bean (never a proxy class).
     *
     * @since 3.0.0
     */
    public Class<? extends Dal<?, ?>> dalBeanClass() {
        return definition.dalClass();
    }
}
