package io.paganbit.telaio.showcase.dal.employee;

import io.paganbit.telaio.showcase.dal.department.Department;

/**
 * Hierarchical Jackson {@code @JsonView} marker classes for {@link Employee} field-level RBAC.
 *
 * <p>The inheritance chain encodes the privilege order: {@code Developer} sees everything an
 * {@code Admin} sees, which in turn includes everything a {@code User} sees. The
 * {@link io.paganbit.telaio.security.adapter.JsonViewDalRbacAdapter} applies the principal's
 * most-privileged view, and Jackson honors the inheritance.</p>
 */
public interface EmployeeView {

    /**
     * Fields visible to any authenticated user. Extends {@link Department.PublicView} so a department
     * nested in an employee response renders its public fields under every employee view.
     */
    interface User extends Department.PublicView {
    }

    /**
     * Adds admin-only fields; includes everything in {@link User}.
     */
    interface Admin extends User {
    }

    /**
     * Adds the most sensitive internal fields; includes everything in {@link Admin}.
     */
    interface Developer extends Admin {
    }
}
