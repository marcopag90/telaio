package io.paganbit.telaio.core.adapter;

import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;

/**
 * Supplies a {@link MethodInterceptor} that applies a cross-cutting concern to a DAL operation adapter.
 *
 * <p>Providers are consulted once per DAL during adapter assembly and applied in {@link Ordered}
 * order, with the lowest order value being the outermost interceptor. The {@code *_PRECEDENCE}
 * constants define the order of the built-in phases and leave room for custom providers between
 * and around them; two providers sharing the same order value are applied in bean definition
 * order.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalAdapterInterceptorProvider extends Ordered {

    /**
     * Order of the exposure phase — the outermost interceptor, applied before audit so that calls to
     * operations a DAL does not expose on the remote boundary are rejected without reaching
     * audit, security, or the {@link io.paganbit.telaio.core.Dal} itself.
     */
    int EXPOSURE_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE;

    /**
     * Order of the audit phase — outermost of the cross-cutting phases, so denied and failed attempts
     * are observed too.
     */
    int AUDIT_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE + 1_000;

    /**
     * Order of the security phase — runs inside audit, outside the base adapter.
     */
    int SECURITY_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE + 2_000;

    /**
     * Returns the interceptor to apply to the given DAL, or {@code null} if this provider does not
     * apply to it.
     *
     * @param context the DAL being assembled
     * @return the interceptor to apply, or {@code null}
     */
    @Nullable MethodInterceptor getInterceptor(DalAdapterContext context);
}
