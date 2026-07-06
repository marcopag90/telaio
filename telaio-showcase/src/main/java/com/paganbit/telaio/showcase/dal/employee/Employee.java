package com.paganbit.telaio.showcase.dal.employee;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.paganbit.telaio.showcase.dal.department.Department;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Demonstrates field-level RBAC via Jackson {@code @JsonView}: the entity is exposed directly and each
 * field declares the {@link EmployeeView} at which it becomes visible/writable. Filtering is performed
 * by {@link EmployeeRbacAdapter} (a {@link com.paganbit.telaio.security.adapter.JsonViewDalRbacAdapter}).
 * Fields without a {@code @JsonView} would be hidden from every role (secure by default).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "employees")
@NamedEntityGraph(
    name = "Employee.withDepartment",
    attributeNodes = @NamedAttributeNode("department")
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonView(EmployeeView.User.class)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    @JsonView(EmployeeView.User.class)
    @JsonProperty("employeeName")
    private String fullName;

    /**
     * The employee's department, exposed as a nested object on read. The association is read-only
     * ({@code @JsonProperty READ_ONLY}): a shared {@code @ManyToOne} reference must not be edited through
     * the child (an RFC 7396 nested merge would mutate the shared parent), so it is re-pointed via the
     * write-only {@link #departmentId} instead. {@code @JsonView(User)} makes it visible to every role;
     * its nested fields appear because {@code EmployeeView.User} extends {@link Department.PublicView}.
     *
     * <p>Fetched {@code LAZY}: the entity keeps lazy loading, and the {@code Employee.withDepartment}
     * {@code @NamedEntityGraph} is applied selectively to the DAL's read methods via
     * {@link EmployeeRepository}'s {@code @EntityGraph} overrides — so the department is loaded on
     * read/list/update (serialized outside the session) without a global EAGER fetch.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", insertable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonView(EmployeeView.User.class)
    private Department department;

    /**
     * Write-only foreign key used to (re-)point {@link #department}: a PATCH carrying
     * {@code {"departmentId": N}} associates the employee with department {@code N} without touching the
     * shared {@code Department} row. Not serialized on output (the nested {@link #department} is shown).
     */
    @Column(name = "department_id")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonView(EmployeeView.User.class)
    private Long departmentId;

    @NotBlank
    @Email
    @Column(nullable = false)
    @JsonView(EmployeeView.Admin.class)
    @JsonProperty("employeeEmail")
    private String email;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    @JsonView(EmployeeView.Developer.class)
    private BigDecimal salary;

    @Column(columnDefinition = "TEXT")
    @JsonView(EmployeeView.Developer.class)
    private String performanceNotes;
}
