package com.paganbit.telaio.showcase.dal.employee;

import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.jpa.JpaDal;
import com.paganbit.telaio.security.adapter.PermitAllDalAuthAdapter;
import com.paganbit.telaio.security.annotation.DalSecurity;

/**
 * <h2>Use case — field-level RBAC via Jackson {@code @JsonView}</h2>
 * <p>
 * Exposes the {@link Employee} entity directly, with CRUD authorized for any authenticated user
 * ({@link PermitAllDalAuthAdapter}) and field-level RBAC delegated to Jackson {@code @JsonView} through
 * {@link EmployeeRbacAdapter} (a {@link com.paganbit.telaio.security.adapter.JsonViewDalRbacAdapter}).
 *
 * <p>This is the alternative to the property-map strategy shown by {@code product}: instead of listing
 * field names per role in the adapter, the authorization policy lives <em>on the entity</em> as
 * {@code @JsonView} annotations, and the adapter only resolves which view a principal gets.
 * <ul>
 *   <li><b>Hierarchical views</b> — {@link EmployeeView} declares {@code Developer ⊃ Admin ⊃ User}. Because
 *       Jackson honors view inheritance, requesting the {@code Developer} view also exposes every field
 *       annotated for {@code Admin} and {@code User}.</li>
 *   <li><b>{@code resolveView}</b> in {@link EmployeeRbacAdapter} returns the principal's most-privileged
 *       view (or {@code null} to deny all fields for an unrecognized role).</li>
 *   <li><b>{@code @JsonProperty} aliases</b> — fields are exposed under JSON names ({@code employeeName},
 *       {@code employeeEmail}); Jackson views operate on the serialized representation, so the
 *       aliases are honored transparently on both input and output.</li>
 *   <li><b>Secure by default</b> — {@code MapperFeature.DEFAULT_VIEW_INCLUSION} is disabled, so any field
 *       <em>without</em> a {@code @JsonView} is hidden from <em>every</em> view.</li>
 * </ul>
 */
@DalService(name = "employees")
@DalSecurity(
    authAdapterClass = PermitAllDalAuthAdapter.class,
    rbacAdapterClass = EmployeeRbacAdapter.class
)
public class EmployeeDalService extends JpaDal<Employee, Long> {
}
