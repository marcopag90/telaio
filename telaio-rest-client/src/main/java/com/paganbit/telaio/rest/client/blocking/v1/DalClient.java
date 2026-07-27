package com.paganbit.telaio.rest.client.blocking.v1;

import com.paganbit.telaio.rest.client.DalPage;
import com.paganbit.telaio.rest.client.DalPageRequest;
import com.paganbit.telaio.rest.client.blocking.TelaioClient;
import com.paganbit.telaio.rest.client.exception.DalClientNotFoundException;
import com.paganbit.telaio.rest.client.exception.DalClientOperationNotExposedException;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * A typed client for one remote DAL, exposing the five operations of the DAL REST API v1.
 * Instances are immutable and thread-safe: obtain them once from
 * {@link TelaioClient#dal(String, Class, Class)} and reuse them.
 * <p>
 * The {@code create} and {@code update} payloads follow JSON Merge Patch semantics (RFC 7396)
 * and are keyed by JSON field names. A {@link Map} payload is sent as given, including its
 * {@code null} values, which the server applies as explicit null-sets. Any other payload is
 * sent with its {@code null} fields dropped; to set a field to {@code null}, use a {@code Map}.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.1.0
 */
public interface DalClient<E, I> {

    /**
     * Creates a new entity ({@code POST /dal/v1/{dalName}}).
     *
     * @param input the properties of the entity to create
     * @return the created entity
     */
    E create(Object input);

    /**
     * Reads a page of entities ({@code GET /dal/v1/{dalName}}).
     *
     * @param filter      a filter expression in Spring Filter syntax using JSON field names, or
     *                    {@code null} for no filtering
     * @param pageRequest paging and sorting parameters
     * @return the requested page
     */
    DalPage<E> read(@Nullable String filter, DalPageRequest pageRequest);

    /**
     * Reads a page of entities without filtering.
     */
    default DalPage<E> read(DalPageRequest pageRequest) {
        return read((String) null, pageRequest);
    }

    /**
     * Reads a page of entities filtered by a programmatically built filter tree
     * ({@code GET /dal/v1/{dalName}}). The client's {@code FilterStringConverter} renders the
     * tree into the same wire expression accepted by {@link #read(String, DalPageRequest)},
     * trading string concatenation for compile-time structure; field names remain JSON field
     * names.
     *
     * @param filter      the filter tree, or {@code null} for no filtering
     * @param pageRequest paging and sorting parameters
     * @return the requested page
     */
    DalPage<E> read(@Nullable FilterNode filter, DalPageRequest pageRequest);

    /**
     * Reads a single entity by identifier ({@code GET /dal/v1/{dalName}/{id}}).
     * <p>
     * The server deliberately does not distinguish "entity absent" from "DAL name unknown", so a
     * misconfigured DAL name also yields empty — except when the target URI exposes no operation
     * at all, which raises {@link DalClientOperationNotExposedException} instead.
     *
     * @param id the entity identifier
     * @return an {@link Optional} containing the entity when found, otherwise empty
     */
    Optional<E> readOne(I id);

    /**
     * Partially updates an entity identified by {@code id} with the provided property values
     * ({@code PATCH /dal/v1/{dalName}/{id}}).
     * <p>
     * Empty means the update was applied but the result is no longer visible under the caller's
     * read scope (RBAC or default filter) — a nonexistent target raises
     * {@link DalClientNotFoundException} instead.
     *
     * @param id    the entity identifier
     * @param patch the sparse set of properties to apply
     * @return the updated entity, or empty if it is no longer visible to the caller
     */
    Optional<E> update(I id, Object patch);

    /**
     * Deletes an entity identified by {@code id} ({@code DELETE /dal/v1/{dalName}/{id}}).
     *
     * @param id the entity identifier
     * @throws DalClientNotFoundException if the entity does not exist
     */
    void delete(I id);
}
