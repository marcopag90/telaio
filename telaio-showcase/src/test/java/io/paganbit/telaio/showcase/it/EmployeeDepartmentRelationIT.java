package io.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use case — updating a {@code @ManyToOne} relationship. {@code Employee} references a shared
 * {@code Department}: the reference is read as a nested object and re-pointed via the write-only
 * {@code departmentId}. Because a shared reference must not be edited through the child (an RFC 7396
 * nested merge would mutate the shared parent), this verifies that re-pointing works and that a nested
 * write against the association is ignored. The {@code departments} resource itself is a plain open CRUD.
 */
class EmployeeDepartmentRelationIT extends AbstractShowcaseIT {

    private static final String EMPLOYEES = "employees";
    private static final String DEPARTMENTS = "departments";

    private long departmentId(String name) {
        for (JsonNode department : tree(list(DEVELOPER, DEPARTMENTS, "size=100")).get("content")) {
            if (name.equals(department.get("name").asString())) {
                return department.get("id").asLong();
            }
        }
        throw new AssertionError("seeded department not found: " + name);
    }

    private long createDepartment(String name) {
        ResponseEntity<String> created = create(USER, DEPARTMENTS, body(Map.of("name", name)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return tree(created).get("id").asLong();
    }

    private String createEmployee(long departmentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeName", "Relation Test");
        payload.put("employeeEmail", "relation-" + System.nanoTime() + "@it.example.com");
        payload.put("salary", new BigDecimal("90000.00"));
        payload.put("departmentId", departmentId); // write-only FK sets the @ManyToOne reference
        ResponseEntity<String> created = create(DEVELOPER, EMPLOYEES, body(payload));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return tree(created).get("id").asString();
    }

    @Test
    void departmentIsReadAsANestedObject() {
        long engineeringId = departmentId("Engineering");
        String employeeId = createEmployee(engineeringId);

        JsonNode department = tree(getOne(DEVELOPER, EMPLOYEES, employeeId)).get("department");
        assertThat(department.isObject()).isTrue();
        assertThat(department.get("id").asLong()).isEqualTo(engineeringId);
        assertThat(department.get("name").asString()).isEqualTo("Engineering");
    }

    @Test
    void repointMovesEmployeeToAnotherDepartmentViaWriteOnlyId() {
        long engineeringId = departmentId("Engineering");
        long designId = departmentId("Design");
        String employeeId = createEmployee(engineeringId);

        // Re-point the @ManyToOne by writing the scalar FK; the shared Department rows are untouched.
        ResponseEntity<String> patched = patch(DEVELOPER, EMPLOYEES, employeeId,
            body(Map.of("departmentId", designId)));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode department = tree(getOne(DEVELOPER, EMPLOYEES, employeeId)).get("department");
        assertThat(department.get("id").asLong()).as("employee now points to Design").isEqualTo(designId);
        assertThat(department.get("name").asString()).isEqualTo("Design");
    }

    @Test
    void nestedWriteAgainstTheSharedReferenceIsIgnored() {
        String uniqueName = "Quality-" + System.nanoTime();
        long departmentId = createDepartment(uniqueName);
        String employeeId = createEmployee(departmentId);

        // Attempt to rename the shared department through the employee. The association is read-only,
        // so the merge ignores it: the shared Department row must not change.
        ResponseEntity<String> patched = patch(DEVELOPER, EMPLOYEES, employeeId, body(Map.of(
            "department", Map.of("id", departmentId, "name", "HACKED")
        )));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode department = tree(getOne(USER, DEPARTMENTS, departmentId));
        assertThat(department.get("name").asString())
            .as("the shared department cannot be edited through an employee")
            .isEqualTo(uniqueName);
    }

    @Test
    void departmentsResourceSupportsCrud() {
        String name = "NewDept-" + System.nanoTime();
        long id = createDepartment(name);

        ResponseEntity<String> fetched = getOne(USER, DEPARTMENTS, id);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(fetched).get("name").asString()).isEqualTo(name);
    }
}
