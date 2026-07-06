package com.paganbit.telaio.jpa;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository interface for JPA DAL repositories.
 * <p>
 * This interface extends {@link JpaRepositoryImplementation} to provide
 * basic CRUD operations for entities in the DAL context.
 * </p>
 *
 * @param <T> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
@NoRepositoryBean
public interface JpaDalRepository<T, I> extends JpaRepositoryImplementation<T, I> {
}
