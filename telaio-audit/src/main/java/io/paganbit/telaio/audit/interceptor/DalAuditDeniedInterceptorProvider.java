package io.paganbit.telaio.audit.interceptor;

import com.turkraft.springfilter.converter.FilterStringConverter;
import io.paganbit.telaio.audit.annotation.DalAudit;
import io.paganbit.telaio.audit.event.DalAuditEventStore;
import io.paganbit.telaio.audit.event.DalAuditOutcomeClassifier;
import io.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import io.paganbit.telaio.core.adapter.DalAdapterContext;
import io.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationUtils;

import java.time.Clock;

/**
 * Contributes the denied-attempt audit interceptor to the operation adapter of each DAL annotated
 * with {@link DalAudit}.
 *
 * <p>Ordered as {@link DalAdapterInterceptorProvider#AUDIT_PRECEDENCE} — outside the security
 * phase, so authorization rejections are observed. This provider is only consulted where the
 * operation-adapter pipeline is assembled (the web module); without it the bean stays dormant and
 * DAL-level auditing still applies.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalAuditDeniedInterceptorProvider implements DalAdapterInterceptorProvider {

    private final DalAuditEventStore store;
    private final DalAuditPrincipalResolver principalResolver;
    private final DalAuditOutcomeClassifier outcomeClassifier;
    private final @Nullable FilterStringConverter filterStringConverter;
    private final Clock clock;

    public DalAuditDeniedInterceptorProvider(
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
    public @Nullable MethodInterceptor getInterceptor(DalAdapterContext context) {
        DalAudit definition = AnnotationUtils.findAnnotation(context.dalBeanClass(), DalAudit.class);
        if (definition == null) {
            return null;
        }
        return new DalAuditDeniedInterceptor(
            context.dalName(),
            DalAuditInterceptorProvider.auditedOperations(definition),
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
}
