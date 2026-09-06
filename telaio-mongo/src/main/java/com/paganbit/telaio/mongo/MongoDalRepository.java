package com.paganbit.telaio.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository interface for Mongo DAL repositories.
 * <p>
 * This interface extends {@link MongoRepository} to provide
 * basic CRUD operations for entities in the DAL context.
 * <p>
 * The DAL never reads through the repository: lists and by-id lookups run as
 * {@link org.springframework.data.mongodb.core.query.Query Query} objects on
 * {@link org.springframework.data.mongodb.core.MongoOperations MongoOperations}. The repository
 * serves {@code save} and {@code delete}, so read-method overrides and read annotations on the
 * repository have no effect on the DAL. Customize reads by overriding
 * {@link MongoDal#executeRead(com.turkraft.springfilter.parser.node.FilterNode, org.springframework.data.domain.Pageable) executeRead}
 * or {@link MongoDal#executeReadOne(Object) executeReadOne} instead.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 2.0.0
 */
@NoRepositoryBean
public interface MongoDalRepository<E, I> extends MongoRepository<E, I> {
}
