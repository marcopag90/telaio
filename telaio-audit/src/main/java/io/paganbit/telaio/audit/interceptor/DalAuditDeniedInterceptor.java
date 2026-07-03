package io.paganbit.telaio.audit.interceptor;

import com.turkraft.springfilter.converter.FilterStringConverter;
import io.paganbit.telaio.audit.event.DalAuditEvent;
import io.paganbit.telaio.audit.event.DalAuditEventStore;
import io.paganbit.telaio.audit.event.DalAuditOutcome;
import io.paganbit.telaio.audit.event.DalAuditOutcomeClassifier;
import io.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import io.paganbit.telaio.core.adapter.DalOperation;
import io.paganbit.telaio.core.adapter.DalOperationType;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

/**
 * Records denied DAL attempts as {@link DalAuditEvent}s at the operation-adapter boundary.
 *
 * <p>A denied attempt never reaches the {@link io.paganbit.telaio.core.Dal} bean, so the DAL-level
 * {@link DalAuditInterceptor} cannot observe it. This interceptor runs outside the security phase
 * and records only failures classified as {@link DalAuditOutcome#DENIED}; every other outcome
 * passes through unrecorded because the DAL-level interceptor already covers it.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalAuditDeniedInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DalAuditDeniedInterceptor.class);

    private final String dalName;
    private final Set<DalOperationType> auditedOperations;
    private final DalAuditEventStore store;
    private final DalAuditPrincipalResolver principalResolver;
    private final DalAuditOutcomeClassifier outcomeClassifier;
    private final DalAuditArgumentSnapshotter argumentSnapshotter;
    private final Clock clock;

    public DalAuditDeniedInterceptor(
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

        final var timestamp = clock.instant();
        final var arguments = argumentSnapshotter.snapshot(op.value(), invocation.getArguments());

        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } catch (Throwable failure) {
            if (outcomeClassifier.classify(failure) == DalAuditOutcome.DENIED) {
                final var event = new DalAuditEvent(
                    timestamp,
                    dalName,
                    op.value(),
                    principalResolver.resolvePrincipal(),
                    arguments,
                    DalAuditOutcome.DENIED,
                    failure.getClass().getName(),
                    failure.getMessage(),
                    Duration.ofNanos(System.nanoTime() - start)
                );
                safeStore(event);
            }
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
}
