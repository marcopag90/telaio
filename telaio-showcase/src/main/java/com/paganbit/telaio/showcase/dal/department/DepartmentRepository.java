package com.paganbit.telaio.showcase.dal.department;

import com.paganbit.telaio.jpa.JpaDalRepository;

import java.util.Optional;

public interface DepartmentRepository extends JpaDalRepository<Department, Long> {

    Optional<Department> findByName(String name);
}
