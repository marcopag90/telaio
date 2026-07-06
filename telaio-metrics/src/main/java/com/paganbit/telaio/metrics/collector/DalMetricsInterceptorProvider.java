package com.paganbit.telaio.metrics.collector;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.interceptor.DalInterceptionContext;
import com.paganbit.telaio.core.interceptor.DalInterceptorProvider;
import com.paganbit.telaio.metrics.annotation.DalMetrics;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Contributes the metrics interceptor to every DAL.
 *
 * <p>Metrics collection is on by default — the inverse of auditing's opt-in — because
 * performance problems tend to appear where nobody thought to opt in. A DAL is excluded with
 * {@code @DalMetrics(enabled = false)}, or measured partially via
 * {@link DalMetrics#operations()}. Ordered as
 * {@link DalInterceptorProvider#METRICS_PRECEDENCE}: inside audit, so timings measure the DAL
 * operation itself rather than the overhead of outer concerns.</p>
 *
 * <p>The interceptor fans the measurement out to every available {@link DalMetricsRecorder}.
 * When none is configured (e.g., the in-house pipeline is disabled and no Micrometer recorder is
 * active), no interceptor is contributed and the DAL incurs no measurement overhead.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalMetricsInterceptorProvider implements DalInterceptorProvider {

    private final List<DalMetricsRecorder> recorders;

    public DalMetricsInterceptorProvider(List<DalMetricsRecorder> recorders) {
        this.recorders = recorders;
    }

    @Override
    public @Nullable MethodInterceptor getInterceptor(DalInterceptionContext context) {
        if (recorders.isEmpty()) {
            return null;
        }
        DalMetrics definition = AnnotationUtils.findAnnotation(context.dalBeanClass(), DalMetrics.class);
        if (definition != null && !definition.enabled()) {
            return null;
        }
        return new DalMetricsInterceptor(context.dalName(), measuredOperations(definition), recorders);
    }

    @Override
    public int getOrder() {
        return METRICS_PRECEDENCE;
    }

    /**
     * Normalizes the annotation's operation set: no annotation or an empty array means all
     * operations.
     */
    Set<DalOperationType> measuredOperations(@Nullable DalMetrics definition) {
        return definition == null || definition.operations().length == 0
            ? EnumSet.allOf(DalOperationType.class)
            : EnumSet.copyOf(List.of(definition.operations()));
    }
}
