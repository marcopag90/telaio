package io.paganbit.telaio.core.registry;

import io.paganbit.telaio.core.Dal;

/**
 * Registry interface used to resolve DAL services dynamically by name.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@SuppressWarnings("squid:S1452")
public interface DalRegistry {

    /**
     * Retrieves a DAL service by its registered name.
     *
     * @param name the DAL name defined in the application context
     * @return the associated DalService
     */
    Dal<?, ?> getServiceByName(String name);

}
