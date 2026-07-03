package io.paganbit.telaio.audit.interceptor;

import io.paganbit.telaio.audit.annotation.DalAudit;
import io.paganbit.telaio.audit.event.DalAuditEventStore;
import io.paganbit.telaio.audit.event.DalAuditOutcomeClassifier;
import io.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import io.paganbit.telaio.core.adapter.DalAdapterContext;
import io.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import io.paganbit.telaio.core.registry.DalManager;
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
class DalAuditDeniedInterceptorProviderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @Mock
    private DalAuditEventStore store;
    @Mock
    private DalAuditPrincipalResolver principalResolver;
    @Mock
    private DalAuditOutcomeClassifier outcomeClassifier;
    @Mock
    private DalManager dalManager;

    private DalAuditDeniedInterceptorProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DalAuditDeniedInterceptorProvider(
            store, principalResolver, outcomeClassifier, null, FIXED_CLOCK);
    }

    @Test
    void dalWithoutDalAudit_shouldNotBeIntercepted() {
        assertThat(provider.getInterceptor(context(NotAudited.class))).isNull();
    }

    @Test
    void dalWithDalAudit_shouldBeIntercepted() {
        assertThat(provider.getInterceptor(context(Audited.class)))
            .isInstanceOf(DalAuditDeniedInterceptor.class);
    }

    @Test
    void order_shouldPlaceAuditOutsideSecurity() {
        assertThat(provider.getOrder()).isEqualTo(DalAdapterInterceptorProvider.AUDIT_PRECEDENCE);
        assertThat(provider.getOrder()).isLessThan(DalAdapterInterceptorProvider.SECURITY_PRECEDENCE);
    }

    private DalAdapterContext context(Class<?> dalBeanClass) {
        return new DalAdapterContext("testDal", dalBeanClass, dalManager);
    }

    @DalAudit
    static class Audited {
    }

    static class NotAudited {
    }
}
