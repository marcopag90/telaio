package com.paganbit.telaio.core.registry;

/**
 * Internal contract for registering DAL service definitions into the DAL registry.
 * <p>
 * This interface is intended solely for library infrastructure components
 * such as bootstrap loaders or registrars. It should not be used directly
 * by application developers. External access to DALs should be performed
 * through {@link DalRegistry}.
 * </p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalRegistryWriter {

    /**
     * Registers a DAL service by its unique name.
     *
     * @param definition the DAL definition
     */
    void register(DalDefinitionEntry definition);
}
