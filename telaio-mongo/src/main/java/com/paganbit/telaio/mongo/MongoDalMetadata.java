package com.paganbit.telaio.mongo;

import com.paganbit.telaio.core.DalMetadata;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;

/**
 * Mongo-specific metadata contract for DAL services.
 * <p>
 * Extends {@link DalMetadata} with access to repository and Spring Data mapping metadata details.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 2.0.0
 */
public interface MongoDalMetadata<E, I> extends DalMetadata<E, I> {

    /**
     * Returns the Mongo repository used for CRUD operations on the entity.
     *
     * @return the Mongo repository
     */
    MongoDalRepository<E, I> getRepository();

    /**
     * Returns the Spring Data {@link MongoPersistentEntity} for the managed entity.
     *
     * @return the Spring Data mapping metadata of the entity
     */
    MongoPersistentEntity<E> getPersistentEntity();
}
