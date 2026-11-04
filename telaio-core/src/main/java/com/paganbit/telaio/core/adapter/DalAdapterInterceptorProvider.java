package com.paganbit.telaio.core.adapter;

import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;

/**
 * Supplies a {@link MethodInterceptor} that applies a cross-cutting concern to a DAL operation adapter.
 *
 * <p>Providers are consulted once per (channel, DAL) chain during adapter assembly and applied in
 * {@link Ordered} order, with the lowest order value being the outermost interceptor after core's own
 * exposure interceptor. The {@code *_PRECEDENCE} constants define the order of the built-in phases and
 * leave room for custom providers between and around them; two providers sharing the same order value
 * are applied in bean definition order. The {@link DalAdapterContext} names the channel, so a provider
 * whose behavior depends on it can read it; a provider that only reports what happened belongs at the
 * {@link com.paganbit.telaio.core.Dal} level instead (see
 * {@link com.paganbit.telaio.core.interceptor.DalInterceptorProvider}).</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalAdapterInterceptorProvider extends Ordered {

    /**
     * Order of the exposure phase — core's own {@link DalExposureInterceptor}, which the assembler
     * always places outermost so that calls to operations a DAL does not expose on the chain's channel
     * are rejected without reaching audit, security, or the {@link com.paganbit.telaio.core.Dal}
     * itself. No provider runs outside it.
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
