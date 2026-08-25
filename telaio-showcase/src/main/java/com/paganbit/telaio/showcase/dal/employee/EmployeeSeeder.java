package com.paganbit.telaio.showcase.dal.employee;

import com.paganbit.telaio.showcase.dal.department.Department;
import com.paganbit.telaio.showcase.dal.department.DepartmentRepository;
import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Demo employees, linked to the departments seeded beforehand (see the reference-data ordering in
 * {@code DepartmentSeeder}); includes the sensitive fields the JsonView RBAC of
 * {@code EmployeeDalService} reveals per role.
 */
@Component
class EmployeeSeeder extends AbstractDemoSeeder {

    private final EmployeeRepository repository;
    private final DepartmentRepository departmentRepository;

    EmployeeSeeder(EmployeeRepository repository, DepartmentRepository departmentRepository) {
        super(repository);
        this.repository = repository;
        this.departmentRepository = departmentRepository;
    }

    @Override
    protected void populate() {
        repository.save(employee("Ada Lovelace", departmentId("Engineering"), "ada@example.com",
            "125000.00", "Top performer; leads the DAL framework initiative."));
        repository.save(employee("Grace Hopper", departmentId("Design"), "grace@example.com",
            "118000.00", "Drives the design system; mentors juniors."));
    }

    private Long departmentId(String name) {
        return departmentRepository.findByName(name).map(Department::getId).orElseThrow();
    }

    private static Employee employee(
        String fullName, Long departmentId, String email, String salary, String performanceNotes
    ) {
        Employee employee = new Employee();
        employee.setFullName(fullName);
        employee.setDepartmentId(departmentId);
        employee.setEmail(email);
        employee.setSalary(new BigDecimal(salary));
        employee.setPerformanceNotes(performanceNotes);
        return employee;
    }
}
