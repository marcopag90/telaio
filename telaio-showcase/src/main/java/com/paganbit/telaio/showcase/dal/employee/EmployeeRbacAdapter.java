package com.paganbit.telaio.showcase.dal.employee;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.security.adapter.JsonViewDalRbacAdapter;
import com.paganbit.telaio.showcase.role.UserRole;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * {@code @JsonView}-based field-level RBAC for {@link Employee}: maps the principal's most-privileged
 * role to the corresponding {@link EmployeeView}, applied to both input and output. Returning
 * {@code null} (no recognized role) denies all fields.
 */
@Component
public class EmployeeRbacAdapter extends JsonViewDalRbacAdapter<Employee> {

    @Override
    protected @Nullable Class<?> resolveView(DalOperationType operation, Authentication authentication) {
        if (hasRole(authentication, UserRole.DEVELOPER)) {
            return EmployeeView.Developer.class;
        }
        if (hasRole(authentication, UserRole.ADMIN)) {
            return EmployeeView.Admin.class;
        }
        if (hasRole(authentication, UserRole.USER)) {
            return EmployeeView.User.class;
        }
        return null;
    }

    private boolean hasRole(@Nullable Authentication authentication, UserRole role) {
        return authentication != null && authentication.getAuthorities().contains(role);
    }
}
