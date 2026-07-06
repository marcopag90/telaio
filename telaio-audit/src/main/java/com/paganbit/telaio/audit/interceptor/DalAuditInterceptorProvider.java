package com.paganbit.telaio.audit.interceptor;

import com.turkraft.springfilter.converter.FilterStringConverter;
import com.paganbit.telaio.audit.annotation.DalAudit;
import com.paganbit.telaio.audit.event.DalAuditEventStore;
import com.paganbit.telaio.audit.event.DalAuditOutcomeClassifier;
import com.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.interceptor.DalInterceptionContext;
import com.paganbit.telaio.core.interceptor.DalInterceptorProvider;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationUtils;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Contributes the audit interceptor to each DAL annotated with {@link DalAudit}.
 *
 * <p>Auditing is opt-in: DALs without the annotation are left untouched. Ordered as
 * {@link DalInterceptorProvider#AUDIT_PRECEDENCE}.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalAuditInterceptorProvider implements DalInterceptorProvider {

    private final DalAuditEventStore store;
    private final DalAuditPrincipalResolver principalResolver;
    private final DalAuditOutcomeClassifier outcomeClassifier;
    private final @Nullable FilterStringConverter filterStringConverter;
    private final Clock clock;

    public DalAuditInterceptorProvider(
        DalAuditEventStore store,
        DalAuditPrincipalResolver principalResolver,
        DalAuditOutcomeClassifier outcomeClassifier,
        @Nullable FilterStringConverter filterStringConverter,
        Clock clock
    ) {
        this.store = store;
        this.principalResolver = principalResolver;
        this.outcomeClassifier = outcomeClassifier;
        this.filterStringConverter = filterStringConverter;
        this.clock = clock;
    }

    @Override
    public @Nullable MethodInterceptor getInterceptor(DalInterceptionContext context) {
        DalAudit definition = AnnotationUtils.findAnnotation(context.dalBeanClass(), DalAudit.class);
        if (definition == null) {
            return null;
        }
        return new DalAuditInterceptor(
            context.dalName(),
            auditedOperations(definition),
            store,
            principalResolver,
            outcomeClassifier,
            filterStringConverter,
            clock
        );
    }

    @Override
    public int getOrder() {
        return AUDIT_PRECEDENCE;
    }

    /**
     * Normalizes the annotation's operation set: empty means all operations.
     */
    static Set<DalOperationType> auditedOperations(DalAudit definition) {
        return definition.operations().length == 0
            ? EnumSet.allOf(DalOperationType.class)
            : EnumSet.copyOf(List.of(definition.operations()));
    }
}
