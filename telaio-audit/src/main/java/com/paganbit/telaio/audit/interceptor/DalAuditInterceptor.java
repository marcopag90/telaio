package com.paganbit.telaio.audit.interceptor;

import com.paganbit.telaio.audit.event.DalAuditEvent;
import com.paganbit.telaio.audit.event.DalAuditEventStore;
import com.paganbit.telaio.audit.event.DalAuditOutcome;
import com.paganbit.telaio.audit.event.DalAuditOutcomeClassifier;
import com.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import com.paganbit.telaio.core.adapter.DalOperation;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Records every audited DAL invocation as a {@link DalAuditEvent}.
 *
 * <p>Applied directly to the {@link com.paganbit.telaio.core.Dal} bean, so it observes every
 * invocation channel. Each operation is identified via its {@link DalOperation} annotation;
 * methods without one, or whose operation is not in the audited set, are passed through unchanged.
 * Failures are rethrown after being recorded, and a failing {@link DalAuditEventStore} never
 * breaks the business operation.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalAuditInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DalAuditInterceptor.class);

    private final String dalName;
    private final Set<DalOperationType> auditedOperations;
    private final DalAuditEventStore store;
    private final DalAuditPrincipalResolver principalResolver;
    private final DalAuditOutcomeClassifier outcomeClassifier;
    private final DalAuditArgumentSnapshotter argumentSnapshotter;
    private final Clock clock;

    public DalAuditInterceptor(
        String dalName,
        Set<DalOperationType> auditedOperations,
        DalAuditEventStore store,
        DalAuditPrincipalResolver principalResolver,
        DalAuditOutcomeClassifier outcomeClassifier,
        @Nullable FilterStringConverter filterStringConverter,
        Clock clock
    ) {
        this.dalName = dalName;
        this.auditedOperations = auditedOperations;
        this.store = store;
        this.principalResolver = principalResolver;
        this.outcomeClassifier = outcomeClassifier;
        this.argumentSnapshotter = new DalAuditArgumentSnapshotter(filterStringConverter);
        this.clock = clock;
    }

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        DalOperation op = AnnotationUtils.findAnnotation(invocation.getMethod(), DalOperation.class);
        if (op == null || !auditedOperations.contains(op.value())) {
            return invocation.proceed();
        }

        // Snapshot before proceeding: inner interceptors may replace or mutate the arguments
        Instant timestamp = clock.instant();
        String principal = principalResolver.resolvePrincipal();
        final var arguments = argumentSnapshotter.snapshot(op.value(), invocation.getArguments());

        long start = System.nanoTime();
        try {
            Object result = invocation.proceed();
            final var event = new DalAuditEvent(
                timestamp,
                dalName,
                op.value(),
                principal,
                arguments,
                DalAuditOutcome.SUCCESS,
                null,
                null,
                elapsedSince(start)
            );
            safeStore(event);
            return result;
        } catch (Throwable failure) {
            final var event = new DalAuditEvent(
                timestamp,
                dalName,
                op.value(),
                principal,
                arguments,
                outcomeClassifier.classify(failure),
                failure.getClass().getName(),
                failure.getMessage(),
                elapsedSince(start)
            );
            safeStore(event);
            throw failure;
        }
    }

    private void safeStore(DalAuditEvent event) {
        try {
            store.store(event);
        } catch (Exception e) {
            log.error("Failed to store audit event for DAL [{}]", dalName, e);
        }
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
