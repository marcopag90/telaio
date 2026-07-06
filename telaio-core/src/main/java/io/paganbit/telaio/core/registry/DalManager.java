package io.paganbit.telaio.core.registry;

import io.paganbit.telaio.core.Dal;

/**
 * Composite registry interface that provides full access to the DAL registry system.
 * <p>
 * This interface combines:
 * <ul>
 *   <li>{@link DalRegistry} – for retrieving active {@link Dal} instances</li>
 *   <li>{@link DalAdapterRegistry} - for retrieving DAL adapters </li>
 *   <li>{@link DalDefinitionRegistry} – for accessing {@link DalDefinitionEntry} metadata</li>
 *   <li>{@link DalRegistryWriter} – for registration of DAL services</li>
 * </ul>
 *
 * <p>
 * This contract is intended for internal use by the Telaio infrastructure.
 * Application developers should typically rely only on {@link DalRegistry} to interact with available DALs.
 * </p>
 *
 * @author Marco Pagan
 * @see Dal
 * @see DalRegistry
 * @see DalAdapterRegistry
 * @see DalDefinitionRegistry
 * @see DalRegistryWriter
 * @since 1.0.0
 */
public interface DalManager
    extends DalRegistry, DalAdapterRegistry, DalDefinitionRegistry, DalRegistryWriter {
}
