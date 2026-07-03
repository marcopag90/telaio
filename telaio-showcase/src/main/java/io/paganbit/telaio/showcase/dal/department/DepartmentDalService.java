package io.paganbit.telaio.showcase.dal.department;

import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.jpa.JpaDal;

/**
 * <h2>Use case — a referenced entity behind a relationship</h2>
 * <p>
 * Plain, open CRUD DAL for {@link Department}. It exists to be <em>referenced</em>: each {@code Employee}
 * holds a {@code @ManyToOne} to a department. The interesting relationship behavior — reading the
 * reference as a nested object and re-pointing it on update — is demonstrated on {@code employee}.
 * <p>
 * Like {@code announcements}, this declares no {@code @DalSecurity}, so it is open by default
 * (PermitAll). A department is a shared reference: it is edited here, through its own resource, and never
 * mutated through the employees that point at it.
 */
@DalService(name = "departments")
public class DepartmentDalService extends JpaDal<Department, Long> {
}
