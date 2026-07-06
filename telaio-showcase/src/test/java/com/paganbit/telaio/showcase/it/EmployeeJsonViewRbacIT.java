package com.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use case — field-level RBAC via Jackson {@code @JsonView}. {@code employees} permits all CRUD
 * (PermitAll) but {@code EmployeeRbacAdapter} maps the principal's most-privileged role to a view in the
 * hierarchy {@code Developer ⊃ Admin ⊃ User}. This verifies the per-role visible field sets on output
 * (using the {@code @JsonProperty} aliases {@code employeeName}/{@code employeeEmail}, never the raw Java
 * names) and that disallowed fields are dropped from write payloads on input.
 */
class EmployeeJsonViewRbacIT extends AbstractShowcaseIT {

    private static final String DAL = "employees";

    /**
     * An employee that has a department to inspect across roles. Picks a seeded row (Ada/Grace are
     * linked to departments) rather than relying on ordering, since other tests create department-less
     * employees.
     */
    private String anyEmployeeId() {
        for (JsonNode employee : tree(list(DEVELOPER, DAL, "size=100")).get("content")) {
            JsonNode department = employee.get("department");
            if (department != null && department.isObject()) {
                return employee.get("id").asString();
            }
        }
        throw new AssertionError("seed data should contain an employee with a department");
    }

    @Test
    void userViewExposesOnlyBaseFieldsUnderTheirAliases() {
        JsonNode employee = tree(getOne(USER, DAL, anyEmployeeId()));

        // Visible to USER
        assertThat(employee.has("id")).as("id is read-only but visible to every role").isTrue();
        assertThat(employee.has("employeeName")).isTrue();
        // department is exposed as a nested object (the @ManyToOne reference), with its public fields
        assertThat(employee.get("department").isObject()).as("department is a nested object").isTrue();
        assertThat(employee.get("department").has("id")).isTrue();
        assertThat(employee.get("department").has("name")).isTrue();
        // Hidden from USER
        assertThat(employee.has("employeeEmail")).isFalse();
        assertThat(employee.has("salary")).isFalse();
        assertThat(employee.has("performanceNotes")).isFalse();
        // Never the raw Java property names — only the @JsonProperty aliases
        assertThat(employee.has("fullName")).isFalse();
        assertThat(employee.has("email")).isFalse();
        // departmentId is write-only: it is accepted on input but never serialized
        assertThat(employee.has("departmentId")).as("departmentId is write-only").isFalse();
    }

    @Test
    void adminViewAddsEmailButNotTheSensitiveFields() {
        JsonNode employee = tree(getOne(ADMIN, DAL, anyEmployeeId()));

        assertThat(employee.has("employeeName")).isTrue();
        assertThat(employee.has("employeeEmail")).as("email becomes visible at ADMIN").isTrue();
        assertThat(employee.has("salary")).isFalse();
        assertThat(employee.has("performanceNotes")).isFalse();
    }

    @Test
    void developerViewExposesEverything() {
        JsonNode employee = tree(getOne(DEVELOPER, DAL, anyEmployeeId()));

        assertThat(employee.has("employeeName")).isTrue();
        assertThat(employee.has("employeeEmail")).isTrue();
        assertThat(employee.has("salary")).as("salary visible at DEVELOPER").isTrue();
        assertThat(employee.has("performanceNotes")).isTrue();
    }

    @Test
    void inputFilteringDropsADisallowedFieldOnUpdate() {
        // Create a full employee as DEVELOPER (only DEVELOPER may write salary/email).
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeName", "RBAC Target");
        payload.put("employeeEmail", "rbac-target@it.example.com");
        payload.put("salary", new BigDecimal("100000.00"));
        payload.put("performanceNotes", "Created by the RBAC integration test.");

        ResponseEntity<String> created = create(DEVELOPER, DAL, body(payload));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = tree(created).get("id").asString();
        BigDecimal originalSalary = new BigDecimal(tree(created).get("salary").asString());

        // USER patches an allowed field (employeeName) and a disallowed one (salary). The salary key is
        // outside the USER view, so it is silently dropped; the response is filtered to the USER view.
        ResponseEntity<String> patched = patch(USER, DAL, id, body(Map.of(
            "employeeName", "Renamed By User",
            "salary", new BigDecimal("1.00")
        )));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(patched).has("salary")).as("USER never sees salary, even on its own write").isFalse();

        // DEVELOPER confirms: the allowed field changed, the disallowed write was ignored.
        JsonNode afterDeveloperView = tree(getOne(DEVELOPER, DAL, id));
        assertThat(afterDeveloperView.get("employeeName").asString()).isEqualTo("Renamed By User");
        assertThat(new BigDecimal(afterDeveloperView.get("salary").asString()))
            .as("USER's attempt to change salary was dropped on input")
            .isEqualByComparingTo(originalSalary);
    }
}
