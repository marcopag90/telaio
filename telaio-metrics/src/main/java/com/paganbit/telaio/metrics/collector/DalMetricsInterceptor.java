package com.paganbit.telaio.metrics.collector;

import com.paganbit.telaio.core.adapter.DalOperation;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.exception.DalFailureKind;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.List;
import java.util.Set;

/**
 * Times every measured DAL invocation and records it in all the configured
 * {@link DalMetricsRecorder}s.
 *
 * <p>Applied directly to the {@link com.paganbit.telaio.core.Dal} bean, so it observes every
 * invocation channel. Each operation is identified via its {@link DalOperation} annotation;
 * methods without one, or whose operation is not in the measured set, are passed through
 * unchanged. Failures are rethrown after being counted, and a failing recorder never breaks
 * the business operation nor the other recorders.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalMetricsInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DalMetricsInterceptor.class);

    private final String dalName;
    private final Set<DalOperationType> measuredOperations;
    private final List<DalMetricsRecorder> recorders;

    public DalMetricsInterceptor(
        String dalName,
        Set<DalOperationType> measuredOperations,
        List<DalMetricsRecorder> recorders
    ) {
        this.dalName = dalName;
        this.measuredOperations = measuredOperations;
        this.recorders = recorders;
    }

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        DalOperation op = AnnotationUtils.findAnnotation(invocation.getMethod(), DalOperation.class);
        if (op == null || !measuredOperations.contains(op.value())) {
            return invocation.proceed();
        }

        long start = System.nanoTime();
        try {
            Object result = invocation.proceed();
            safeRecord(op.value(), System.nanoTime() - start, DalMetricsOutcome.SUCCESS);
            return result;
        } catch (Throwable failure) {
            DalMetricsOutcome outcome = DalFailureKind.of(failure).isClientFault()
                ? DalMetricsOutcome.CLIENT_ERROR
                : DalMetricsOutcome.ERROR;
            safeRecord(op.value(), System.nanoTime() - start, outcome);
            throw failure;
        }
    }

    private void safeRecord(DalOperationType operation, long durationNanos, DalMetricsOutcome outcome) {
        for (DalMetricsRecorder recorder : recorders) {
            try {
                recorder.doRecord(dalName, operation, durationNanos, outcome);
            } catch (Exception e) {
                log.error("Failed to record metrics for DAL [{}] with recorder [{}]",
                    dalName, recorder.getClass().getSimpleName(), e);
            }
        }
    }
}
