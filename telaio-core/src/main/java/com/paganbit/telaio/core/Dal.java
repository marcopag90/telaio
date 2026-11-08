package com.paganbit.telaio.core;

import com.paganbit.telaio.core.adapter.DalOperation;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.exception.DalEntityNotFoundException;
import com.paganbit.telaio.core.exception.DalEntityValidationException;
import com.paganbit.telaio.core.exception.DalInvalidSortException;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * The CRUD contract of a DAL (Data Access Layer) over one entity type.
 *
 * <p>Writes take the entity as a map of property names to values, so callers never build entity
 * instances themselves; reads return the entity directly, since Telaio has no DTO layer. Each
 * operation carries a {@link DalOperation} marker naming its {@link DalOperationType}, which the
 * interception and exposure layers use to recognize it.</p>
 *
 * <p><strong>Visibility.</strong> An implementation may restrict the set of entities it works on. An
 * entity outside that set is <em>not visible</em> and behaves exactly like a missing one in every
 * operation: reads do not return it, while an update or a delete fails as if it did not exist.
 * Callers cannot tell the two cases apart, by design.</p>
 *
 * <p>The exceptions named below are unchecked. They are part of the contract: the layers built on
 * top of a DAL translate them into their own failures, so every implementation must raise them under
 * the stated conditions.</p>
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface Dal<E, I> extends DalMetadata<E, I> {

    /**
     * Creates and persists a new entity from the given property values.
     *
     * @param properties the entity's properties, keyed by property name
     * @return the persisted entity, with any value assigned on insert (such as a generated identifier)
     * @throws DalEntityValidationException if the resulting entity fails validation
     */
    @DalOperation(DalOperationType.CREATE)
    E create(Map<String, Object> properties);

    /**
     * Reads a page of visible entities matching the given filter.
     *
     * <p>Pages are stable in two cases: the request has no sort, or it sorts by properties that
     * together identify an entity. Consecutive pages then neither skip nor repeat an entity. A sort by
     * a non-unique property leaves the order of ties to the backend, so a caller who needs stable
     * pages with such a sort adds the identifier as the last criterion.</p>
     *
     * @param filter   the filter to apply, or {@code null} to select every visible entity
     * @param pageable the page to read and the sort to apply; when unsorted, the implementation
     *                 supplies a deterministic order
     * @return the matching entities, never {@code null}; empty when nothing matches
     * @throws DalInvalidSortException if a sort property is not a property of the entity
     */
    @DalOperation(DalOperationType.READ)
    Page<E> read(@Nullable FilterNode filter, Pageable pageable);

    /**
     * Reads the entity with the given identifier.
     *
     * @param id the entity identifier
     * @return the entity, or empty if no visible entity has that identifier
     */
    @DalOperation(DalOperationType.READ_ONE)
    Optional<E> readOne(I id);

    /**
     * Applies the given property values to the entity with the given identifier, then persists it.
     *
     * <p>This is a partial update: only the properties present in the map change, while the others
     * keep their current values. Validation applies to the whole resulting entity, not only to the
     * properties sent.</p>
     *
     * @param id         the entity identifier
     * @param properties the properties to change, keyed by property name
     * @return the entity as it is after the update, or empty if it is no longer visible afterward
     * @throws DalEntityNotFoundException   if no visible entity has that identifier
     * @throws DalEntityValidationException if the resulting entity fails validation
     */
    @DalOperation(DalOperationType.UPDATE)
    Optional<E> update(I id, Map<String, Object> properties);

    /**
     * Deletes the entity with the given identifier.
     *
     * @param id the entity identifier
     * @throws DalEntityNotFoundException if no visible entity has that identifier
     */
    @DalOperation(DalOperationType.DELETE)
    void delete(I id);
}
