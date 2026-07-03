package io.paganbit.telaio.core.registry;

import java.util.Collection;

/**
 * Registry interface used to resolve DAL definitions dynamically by name.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalDefinitionRegistry {

    /**
     * Retrieves a DAL definition by its registered name.
     *
     * @param name the DAL name defined in the application context
     * @return the associated DalRegistryDefinition
     */
    DalDefinitionEntry getDefinitionByName(String name);

    /**
     * Returns all registered DAL definitions.
     *
     * @return an unmodifiable collection of all registered definitions
     */
    Collection<DalDefinitionEntry> getAllDefinitions();
}