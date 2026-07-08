package com.paganbit.telaio.audit.autoconfigure;

import com.paganbit.telaio.audit.annotation.DalAudit;
import com.paganbit.telaio.audit.event.DalAuditEvent;
import com.paganbit.telaio.audit.event.DalAuditEventStore;
import com.paganbit.telaio.audit.event.DalAuditOutcome;
import com.paganbit.telaio.audit.event.SecurityDalAuditOutcomeClassifier;
import com.paganbit.telaio.audit.event.format.DalAuditEventFormatter;
import com.paganbit.telaio.audit.event.format.JsonDalAuditEventFormatter;
import com.paganbit.telaio.audit.event.format.LogfmtDalAuditEventFormatter;
import com.paganbit.telaio.audit.interceptor.DalAuditDeniedInterceptorProvider;
import com.paganbit.telaio.audit.principal.SecurityContextDalAuditPrincipalResolver;
import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies channel-agnostic auditing: no telaio-web class is involved — DALs are invoked
 * programmatically, and the audit proxy applied by the core interception infrastructure records
 * the events.
 */
class TelaioAuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            TelaioCoreAutoConfiguration.class, TelaioAuditAutoConfiguration.class))
        .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new);

    @Test
    void auditedDal_invokedProgrammatically_shouldStoreEvents() {
        contextRunner
            .withBean(RecordingDalAuditEventStore.class)
            .withBean(AuditedStubDal.class)
            .run(context -> {
                AuditedStubDal dal = context.getBean(AuditedStubDal.class);
                assertThat(AopUtils.isCglibProxy(dal)).isTrue();

                dal.create(Map.of("name", "Widget"));

                RecordingDalAuditEventStore store = context.getBean(RecordingDalAuditEventStore.class);
                assertThat(store.events).hasSize(1);
                DalAuditEvent event = store.events.getFirst();
                assertThat(event.dalName()).isEqualTo("audited-stubs");
                assertThat(event.operation()).isEqualTo(DalOperationType.CREATE);
                assertThat(event.outcome()).isEqualTo(DalAuditOutcome.SUCCESS);
                assertThat(event.principal()).isNull();
                assertThat(event.arguments()).isEqualTo(Map.of("input", Map.of("name", "Widget")));
            });
    }

    @Test
    void dalWithoutDalAudit_shouldNotBeProxiedNorAudited() {
        contextRunner
            .withBean(RecordingDalAuditEventStore.class)
            .withBean(PlainStubDal.class)
            .run(context -> {
                PlainStubDal dal = context.getBean(PlainStubDal.class);
                assertThat(AopUtils.isAopProxy(dal)).isFalse();

                dal.create(Map.of("name", "Widget"));

                assertThat(context.getBean(RecordingDalAuditEventStore.class).events).isEmpty();
            });
    }

    @Test
    void userDefinedStore_shouldReplaceTheLoggingDefault() {
        contextRunner
            .withBean(RecordingDalAuditEventStore.class)
            .run(context -> assertThat(context.getBean(DalAuditEventStore.class))
                .isInstanceOf(RecordingDalAuditEventStore.class));
    }

    @Test
    void defaultFormat_shouldRegisterLogfmtFormatter() {
        contextRunner.run(context -> assertThat(context.getBean(DalAuditEventFormatter.class))
            .isInstanceOf(LogfmtDalAuditEventFormatter.class));
    }

    @Test
    void jsonFormat_shouldRegisterJsonFormatter() {
        contextRunner
            .withPropertyValues("telaio.audit.logging.format=JSON")
            .run(context -> assertThat(context.getBean(DalAuditEventFormatter.class))
                .isInstanceOf(JsonDalAuditEventFormatter.class));
    }

    @Test
    void loggingProperties_shouldBind() {
        contextRunner
            .withPropertyValues(
                "telaio.audit.logging.format=JSON",
                "telaio.audit.logging.category=my.audit",
                "telaio.audit.logging.include-mdc=false")
            .run(context -> {
                TelaioAuditProperties.Logging logging =
                    context.getBean(TelaioAuditProperties.class).getLogging();
                assertThat(logging.getFormat()).isEqualTo(TelaioAuditProperties.Format.JSON);
                assertThat(logging.getCategory()).isEqualTo("my.audit");
                assertThat(logging.isIncludeMdc()).isFalse();
            });
    }

    @Test
    void userDefinedFormatter_shouldReplaceTheDefault() {
        DalAuditEventFormatter custom = event -> "custom";
        contextRunner
            .withBean(DalAuditEventFormatter.class, () -> custom)
            .run(context -> assertThat(context.getBean(DalAuditEventFormatter.class)).isSameAs(custom));
    }

    @Test
    void withSpringSecurityOnClasspath_shouldRegisterSecurityAwareBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SecurityContextDalAuditPrincipalResolver.class);
            assertThat(context).hasSingleBean(SecurityDalAuditOutcomeClassifier.class);
            assertThat(context).hasSingleBean(DalAuditDeniedInterceptorProvider.class);
        });
    }

    static class RecordingDalAuditEventStore implements DalAuditEventStore {

        final List<DalAuditEvent> events = new ArrayList<>();

        @Override
        public void store(DalAuditEvent event) {
            events.add(event);
        }
    }

    static class StubDal implements Dal<Object, Long> {

        @Override
        public Object create(Map<String, Object> properties) {
            return properties;
        }

        @Override
        public Page<Object> read(@Nullable FilterNode filter, Pageable pageable) {
            return new PageImpl<>(List.of());
        }

        @Override
        public Optional<Object> readOne(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<Object> update(Long id, Map<String, Object> properties) {
            return Optional.empty();
        }

        @Override
        public void delete(Long id) {
            //noop
        }

        @Override
        public Class<Object> getEntityClass() {
            return Object.class;
        }

        @Override
        public Class<Long> getIdClass() {
            return Long.class;
        }
    }

    @DalService(name = "audited-stubs")
    @DalAudit
    static class AuditedStubDal extends StubDal {
    }

    @DalService(name = "plain-stubs")
    static class PlainStubDal extends StubDal {
    }
}
