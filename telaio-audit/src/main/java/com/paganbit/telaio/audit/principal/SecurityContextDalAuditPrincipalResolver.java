package com.paganbit.telaio.audit.principal;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link DalAuditPrincipalResolver} that reads the principal name from the current Spring
 * Security context.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class SecurityContextDalAuditPrincipalResolver implements DalAuditPrincipalResolver {

    @Override
    public @Nullable String resolvePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}
