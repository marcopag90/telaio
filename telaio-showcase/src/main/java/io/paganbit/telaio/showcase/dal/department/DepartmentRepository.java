package io.paganbit.telaio.showcase.dal.department;

import io.paganbit.telaio.jpa.JpaDalRepository;

import java.util.Optional;

public interface DepartmentRepository extends JpaDalRepository<Department, Long> {

    Optional<Department> findByName(String name);
}
