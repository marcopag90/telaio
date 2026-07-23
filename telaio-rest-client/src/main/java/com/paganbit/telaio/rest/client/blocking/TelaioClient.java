package com.paganbit.telaio.rest.client.blocking;

import com.paganbit.telaio.rest.client.blocking.v1.DalClient;

/**
 * The entry point to one remote Telaio application. Each call to {@link #dal} returns a typed
 * {@link DalClient} for one of the DALs that the remote application exposes. Implementations are
 * thread-safe.
 * <p>
 * This interface is the version-neutral hub over the versioned wire contracts: each accessor is
 * pinned to one contract version ({@link #dal} to {@code /dal/v1}) and a future revision arrives
 * as a new accessor on the same instance, so one connection serves every version at once and
 * call sites migrate one at a time. For that reason this interface is not intended to be
 * implemented by applications — obtain instances from the registry or a factory and expect new
 * accessors to appear in future releases.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public interface TelaioClient {

    /**
     * Returns the typed {@link DalClient} for the named remote DAL.
     *
     * @param dalName    the DAL service name exposed by the remote application
     * @param entityType the entity class
     * @param idType     the entity identifier class
     * @param <E>        the entity type
     * @param <I>        the entity identifier type
     * @return an immutable, thread-safe client
     */
    <E, I> DalClient<E, I> dal(String dalName, Class<E> entityType, Class<I> idType);
}
