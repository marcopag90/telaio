package io.paganbit.telaio.showcase.dal.employee;

import io.paganbit.telaio.jpa.JpaDalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

/**
 * Repository for {@link Employee}. Demonstrates applying a JPA {@link jakarta.persistence.NamedEntityGraph
 * entity graph} to the read methods the DAL uses, so the {@code LAZY} {@code department} association is
 * fetched on those paths (and serialized outside the session) without a global EAGER fetch.
 *
 * <p>{@code JpaDal} reads exclusively through these methods — {@code findOne(Specification)} for
 * {@code readOne} (also the re-read at the end of {@code update}) and {@code findAll(...)} for the list —
 * so overriding them with {@code @EntityGraph("Employee.withDepartment")} is enough to cover every read.
 * Writes ({@code save}, {@code deleteById}) and {@code findById} (unused by the DAL) need no graph.</p>
 */
public interface EmployeeRepository extends JpaDalRepository<Employee, Long> {

    @EntityGraph("Employee.withDepartment")
    @Override
    Optional<Employee> findOne(Specification<Employee> spec);

    @EntityGraph("Employee.withDepartment")
    @Override
    Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);

    @EntityGraph("Employee.withDepartment")
    @Override
    Page<Employee> findAll(Pageable pageable);
}
