package com.paganbit.telaio.jpa;

import com.paganbit.telaio.core.DalMetadata;
import jakarta.persistence.metamodel.EntityType;

/**
 * JPA-specific metadata contract for DAL services.
 * <p>
 * Extends {@link DalMetadata} with access to repository and JPA metamodel details.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface JpaDalMetadata<E, I> extends DalMetadata<E, I> {

    /**
     * Returns the JPA repository used for CRUD operations on the entity.
     *
     * @return the JPA repository
     */
    JpaDalRepository<E, I> getRepository();

    /**
     * Returns the JPA {@link EntityType} for the managed entity.
     *
     * @return the JPA metamodel entity type
     */
    EntityType<E> getEntityType();
}
