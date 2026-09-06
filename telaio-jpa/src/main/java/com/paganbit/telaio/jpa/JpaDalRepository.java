package com.paganbit.telaio.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository interface for JPA DAL repositories.
 * <p>
 * This interface extends {@link JpaRepositoryImplementation} to provide
 * basic CRUD operations for entities in the DAL context.
 * <p>
 * The base {@code JpaDal} reads through exactly two methods: {@link #findOne(Specification)} for single-entity
 * reads and {@link #findAll(Specification, Pageable)} for lists. A list without a filter still uses
 * the latter, with {@link Specification#unrestricted()}. Overriding those two methods is enough to
 * customize every read, for example to apply an entity graph:
 * <pre>{@code
 * public interface EmployeeRepository extends JpaDalRepository<Employee, Long> {
 *
 *     @EntityGraph("Employee.withDepartment")
 *     @Override
 *     Optional<Employee> findOne(Specification<Employee> spec);
 *
 *     @EntityGraph("Employee.withDepartment")
 *     @Override
 *     Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);
 * }
 * }</pre>
 *
 * @param <T> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
@NoRepositoryBean
public interface JpaDalRepository<T, I> extends JpaRepositoryImplementation<T, I> {
}
