package io.paganbit.telaio.audit.principal;

import org.jspecify.annotations.Nullable;

/**
 * Resolves the principal to record on audit events.
 *
 * <p>Provide a custom bean to derive the principal from a source other than the default Spring
 * Security context — for example, a message header or a tenant-aware context holder.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@FunctionalInterface
public interface DalAuditPrincipalResolver {

    /**
     * Returns the name of the principal performing the current operation.
     *
     * @return the principal name, or {@code null} when the invocation is unauthenticated
     */
    @Nullable String resolvePrincipal();
}
