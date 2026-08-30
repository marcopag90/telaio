package com.paganbit.telaio.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository interface for Mongo DAL repositories.
 * <p>
 * This interface extends {@link MongoRepository} to provide
 * basic CRUD operations for entities in the DAL context.
 * </p>
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.2.0
 */
@NoRepositoryBean
public interface MongoDalRepository<E, I> extends MongoRepository<E, I> {
}
