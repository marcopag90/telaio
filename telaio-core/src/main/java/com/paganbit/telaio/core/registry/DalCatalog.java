package com.paganbit.telaio.core.registry;

/**
 * Read-only view of the DAL registry: resolves {@link com.paganbit.telaio.core.Dal} instances and their
 * {@link DalDefinitionEntry} metadata by name, without the ability to register new ones.
 *
 * <p>This is the contract handed to interception providers and other collaborators that need to look DALs
 * up but must not alter the registry. {@link DalManager} extends it with registration.</p>
 *
 * @author Marco Pagan
 * @see DalRegistry
 * @see DalDefinitionRegistry
 * @since 3.0.0
 */
public interface DalCatalog extends DalRegistry, DalDefinitionRegistry {
}
