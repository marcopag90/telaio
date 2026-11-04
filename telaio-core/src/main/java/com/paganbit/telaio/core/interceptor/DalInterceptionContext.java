package com.paganbit.telaio.core.interceptor;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;

/**
 * Supplies a {@link DalInterceptorProvider} with the details of the {@link Dal} bean being
 * processed.
 *
 * <p>Create instances with {@link #of(DalDefinitionEntry)}: the canonical constructor is an internal
 * detail, so a component added later is a new record component behind the factory, not a signature change
 * for the SPI.</p>
 *
 * @param definition the identity of the DAL being processed
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalInterceptionContext(DalDefinitionEntry definition) {

    /**
     * Creates the context of the given DAL.
     *
     * @param definition the identity of the DAL being processed
     * @return the context
     * @since 3.0.0
     */
    public static DalInterceptionContext of(DalDefinitionEntry definition) {
        return new DalInterceptionContext(definition);
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
