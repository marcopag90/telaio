package com.paganbit.telaio.showcase.dal.department;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A department, the shared entity referenced by {@code Employee} via {@code @ManyToOne}. Exposed as its
 * own open CRUD resource by {@code DepartmentDalService}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "departments")
public class Department {

    /**
     * View marker for the department fields safe to expose wherever a {@code Department} is shown —
     * including nested inside another entity served through a {@code @JsonView}-based RBAC adapter.
     *
     * <p>{@code EmployeeView.User} extends this marker, so a department nested in an employee response is
     * visible to every employee role without coupling this entity to the employee's view hierarchy (the
     * dependency points the natural way: employee → department).</p>
     */
    public interface PublicView {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonView(PublicView.class)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    @JsonView(PublicView.class)
    private String name;
}
