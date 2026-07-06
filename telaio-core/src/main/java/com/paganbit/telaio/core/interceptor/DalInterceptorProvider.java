package com.paganbit.telaio.core.interceptor;

import com.paganbit.telaio.core.Dal;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;

/**
 * Supplies a {@link MethodInterceptor} that applies a cross-cutting concern directly to a
 * {@link Dal} bean.
 *
 * <p>Unlike {@link com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider}, which targets the
 * operation adapter assembled at the remote boundary, providers of this type intercept the {@link Dal}
 * bean itself. The resulting interceptor therefore applies to every invocation channel — remote or
 * programmatic — without requiring any other Telaio module.</p>
 *
 * <p>Providers are consulted once per {@link Dal} bean during context startup and applied in
 * {@link Ordered} order, with the lowest order value being the outermost interceptor. Two
 * providers sharing the same order value are applied in bean definition order.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalInterceptorProvider extends Ordered {

    /**
     * Order of the audit phase — outermost, so it observes the outcome of every inner concern.
     */
    int AUDIT_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE + 1_000;

    /**
     * Order of the metrics phase — inside audit, so timings measure the DAL operation itself
     * rather than the overhead of outer concerns.
     */
    int METRICS_PRECEDENCE = AUDIT_PRECEDENCE + 1_000;

    /**
     * Returns the interceptor to apply to the given DAL, or {@code null} if this provider does not
     * apply to it.
     *
     * @param context the DAL being processed
     * @return the interceptor to apply, or {@code null}
     */
    @Nullable MethodInterceptor getInterceptor(DalInterceptionContext context);
}
