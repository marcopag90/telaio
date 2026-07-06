package com.paganbit.telaio.audit.interceptor;

import com.paganbit.telaio.audit.annotation.DalAudit;
import com.paganbit.telaio.audit.event.DalAuditEventStore;
import com.paganbit.telaio.audit.event.DalAuditOutcomeClassifier;
import com.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.interceptor.DalInterceptionContext;
import com.paganbit.telaio.core.interceptor.DalInterceptorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DalAuditInterceptorProviderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @Mock
    private DalAuditEventStore store;
    @Mock
    private DalAuditPrincipalResolver principalResolver;
    @Mock
    private DalAuditOutcomeClassifier outcomeClassifier;

    private DalAuditInterceptorProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DalAuditInterceptorProvider(
            store, principalResolver, outcomeClassifier, null, FIXED_CLOCK);
    }

    @Test
    void dalWithoutDalAudit_shouldNotBeIntercepted() {
        assertThat(provider.getInterceptor(context(NotAuditedDal.class))).isNull();
    }

    @Test
    void dalWithDalAudit_shouldBeIntercepted() {
        assertThat(provider.getInterceptor(context(AuditedDal.class)))
            .isInstanceOf(DalAuditInterceptor.class);
    }

    @Test
    void emptyOperations_shouldMeanAllOperations() {
        DalAudit definition = AuditedDal.class.getAnnotation(DalAudit.class);

        assertThat(DalAuditInterceptorProvider.auditedOperations(definition))
            .containsExactlyInAnyOrder(DalOperationType.values());
    }

    @Test
    void explicitOperations_shouldBeAuditedExclusively() {
        DalAudit definition = PartiallyAuditedDal.class.getAnnotation(DalAudit.class);

        assertThat(DalAuditInterceptorProvider.auditedOperations(definition))
            .containsExactlyInAnyOrder(DalOperationType.CREATE, DalOperationType.DELETE);
    }

    @Test
    void order_shouldBeAuditPrecedence() {
        assertThat(provider.getOrder()).isEqualTo(DalInterceptorProvider.AUDIT_PRECEDENCE);
    }

    private static DalInterceptionContext context(Class<? extends Dal<?, ?>> dalBeanClass) {
        return new DalInterceptionContext("testDal", dalBeanClass);
    }

    @DalAudit
    abstract static class AuditedDal implements Dal<Object, Long> {
    }

    @DalAudit(operations = {DalOperationType.CREATE, DalOperationType.DELETE})
    abstract static class PartiallyAuditedDal implements Dal<Object, Long> {
    }

    abstract static class NotAuditedDal implements Dal<Object, Long> {
    }
}
