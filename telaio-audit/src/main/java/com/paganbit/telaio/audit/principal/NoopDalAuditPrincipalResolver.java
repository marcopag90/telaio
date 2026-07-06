package com.paganbit.telaio.audit.principal;

import org.jspecify.annotations.Nullable;

/**
 * Fallback {@link DalAuditPrincipalResolver} that resolves no principal.
 *
 * <p>Used when Spring Security is not on the classpath and no authentication concept exists.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class NoopDalAuditPrincipalResolver implements DalAuditPrincipalResolver {

    @Override
    public @Nullable String resolvePrincipal() {
        return null;
    }
}
