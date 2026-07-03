package io.paganbit.telaio.core.adapter;

import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Exposes the CRUD operations of a DAL as entity-returning methods.
 *
 * <p>An instance is assembled for each registered DAL and invoked by callers. The exposed entity is
 * the single object on the boundary (no DTO layer): cross-cutting behavior is applied around it through
 * {@link DalAdapterInterceptorProvider}.</p>
 *
 * @param <I> the entity identifier type
 * @param <E> the entity type returned to the caller
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalOperationAdapter<I, E> {

    /**
     * Creates an entity from the given properties.
     *
     * @param input the properties of the entity to create
     * @return the created entity
     */
    @DalOperation(DalOperationType.CREATE)
    E create(Map<String, Object> input);

    /**
     * Reads a page of entities matching the given filter.
     *
     * @param filter   the filter to apply, or {@code null} for no filtering
     * @param pageable the pagination and sorting information
     * @return a page of entities
     */
    @DalOperation(DalOperationType.READ)
    Page<E> read(@Nullable FilterNode filter, Pageable pageable);

    /**
     * Reads a single entity by its identifier.
     *
     * @param id the entity identifier
     * @return the entity
     */
    @DalOperation(DalOperationType.READ_ONE)
    E readOne(I id);

    /**
     * Applies a partial update to the entity with the given identifier.
     *
     * @param id    the entity identifier
     * @param patch the properties to update
     * @return the updated entity, or {@link Optional#empty()} if no entity was updated
     */
    @DalOperation(DalOperationType.UPDATE)
    Optional<E> update(I id, Map<String, Object> patch);

    /**
     * Deletes the entity with the given identifier.
     *
     * @param id the entity identifier
     */
    @DalOperation(DalOperationType.DELETE)
    void delete(I id);
}
