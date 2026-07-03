package io.paganbit.telaio.core;

import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.adapter.DalOperation;
import io.paganbit.telaio.core.adapter.DalOperationType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Defines the operations available for a DAL implementation.
 * <p>
 * This contract provides generic CRUD operations using dynamic property maps for create/update,
 * optional lookup by identifier, and paginated/filterable reads.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface Dal<E, I> extends DalMetadata<E, I> {

    /**
     * Creates and persists a new entity instance using the provided property values.
     *
     * @param properties a map of entity property names and values to apply
     * @return the created and persisted entity
     */
    @DalOperation(DalOperationType.CREATE)
    E create(Map<String, Object> properties);

    /**
     * Reads entities matching the provided filter in a paginated form.
     *
     * @param filter   the optional filter expression; when {@code null}, all entities are eligible
     * @param pageable pagination and sorting configuration
     * @return a page containing entities that match the filter
     */
    @DalOperation(DalOperationType.READ)
    Page<E> read(@Nullable FilterNode filter, Pageable pageable);

    /**
     * Reads a single entity by identifier.
     *
     * @param id the entity identifier
     * @return an {@link Optional} containing the entity when found, otherwise empty
     */
    @DalOperation(DalOperationType.READ_ONE)
    Optional<E> readOne(I id);

    /**
     * Updates an existing entity identified by {@code id} with the provided property values.
     *
     * @param id         the entity identifier
     * @param properties a map of entity property names and values to update
     * @return an {@link Optional} containing the updated entity when the target exists, otherwise empty
     */
    @DalOperation(DalOperationType.UPDATE)
    Optional<E> update(I id, Map<String, Object> properties);

    /**
     * Deletes an entity identified by {@code id}.
     *
     * @param id the entity identifier
     */
    @DalOperation(DalOperationType.DELETE)
    void delete(I id);
}
