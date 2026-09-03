package com.paganbit.telaio.showcase.dal.department;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import com.paganbit.telaio.showcase.seed.DemoSeeder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Demo departments — reference data the employee seeder looks up by name, hence
 * {@link DemoSeeder#REFERENCE_DATA} ordering.
 */
@Component
@Order(DemoSeeder.REFERENCE_DATA)
class DepartmentSeeder extends AbstractDemoSeeder {

    private final DepartmentRepository repository;

    DepartmentSeeder(DepartmentRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        repository.save(department("Engineering"));
        repository.save(department("Design"));
    }

    private static Department department(String name) {
        Department department = new Department();
        department.setName(name);
        return department;
    }
}
