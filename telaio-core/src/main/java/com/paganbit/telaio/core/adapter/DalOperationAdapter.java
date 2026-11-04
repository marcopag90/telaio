package com.paganbit.telaio.core.adapter;

import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Exposes the CRUD operations of a DAL to a channel.
 *
 * <p>An instance is assembled for each (channel, DAL) pair and invoked by that channel. There is no DTO
 * layer: every result is the entity itself, or its role-filtered JSON projection when a cross-cutting
 * concern woven around the adapter (through {@link DalAdapterInterceptorProvider}) replaces it. That is
 * why results are typed {@code Object}: a channel serializes whatever comes back, it does not inspect
 * it.</p>
 *
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalOperationAdapter<I> {

    /**
     * Creates an entity from the given properties.
     *
     * @param input the properties of the entity to create
     * @return the created entity, or its projection
     */
    @DalOperation(DalOperationType.CREATE)
    Object create(Map<String, Object> input);

    /**
     * Reads a page of entities matching the given filter.
     *
     * @param filter   the filter to apply, or {@code null} for no filtering
     * @param pageable the pagination and sorting information
     * @return a page of entities, or of their projections
     */
    @DalOperation(DalOperationType.READ)
    Page<Object> read(@Nullable FilterNode filter, Pageable pageable);

    /**
     * Reads a single entity by its identifier.
     *
     * @param id the entity identifier
     * @return the entity, or its projection
     */
    @DalOperation(DalOperationType.READ_ONE)
    Object readOne(I id);

    /**
     * Applies a partial update to the entity with the given identifier.
     *
     * @param id    the entity identifier
     * @param patch the properties to update
     * @return the updated entity (or its projection), or {@link Optional#empty()} if no entity was updated
     */
    @DalOperation(DalOperationType.UPDATE)
    Optional<Object> update(I id, Map<String, Object> patch);

    /**
     * Deletes the entity with the given identifier.
     *
     * @param id the entity identifier
     */
    @DalOperation(DalOperationType.DELETE)
    void delete(I id);
}
