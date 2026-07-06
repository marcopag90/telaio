package com.paganbit.telaio.showcase.dal.bulletin;

import com.paganbit.telaio.security.adapter.DalAuthAdapter;
import com.paganbit.telaio.showcase.role.UserRole;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Identity-aware CRUD authorization: reads are allowed for any authenticated principal, while writes
 * (create/update/delete) are allowed only for the {@code ADMIN} role.
 *
 * <p>This is the kind of decision the authorization layer exists for — it inspects the
 * {@link Authentication} per request, something structural per-operation exposure cannot express. A
 * denied write returns an auditable {@code 403}, and the write endpoints remain part of the documented
 * contract. Registered as a {@code @Component} so {@code DalManager.getAdapter(...)} can resolve it by
 * class.</p>
 */
@Component
public class AdminWritesDalAuthAdapter implements DalAuthAdapter<Long> {

    @Override
    public boolean authorizeRead(Authentication authentication) {
        return true;
    }

    @Override
    public boolean authorizeReadOne(Authentication authentication, Long id) {
        return true;
    }

    @Override
    public boolean authorizeCreate(Authentication authentication) {
        return isAdmin(authentication);
    }

    @Override
    public boolean authorizeUpdate(Authentication authentication, Long id) {
        return isAdmin(authentication);
    }

    @Override
    public boolean authorizeDelete(Authentication authentication, Long id) {
        return isAdmin(authentication);
    }

    private static boolean isAdmin(@Nullable Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(UserRole.ADMIN::equals);
    }
}
