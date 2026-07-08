package com.paganbit.telaio.metrics.autoconfigure;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.metrics.collector.*;
import com.paganbit.telaio.metrics.endpoint.TelaioMetricsEndpoint;
import com.paganbit.telaio.metrics.model.DalMetricsStats;
import com.paganbit.telaio.metrics.store.DalMetricsBucketMerger;
import com.paganbit.telaio.metrics.store.DalMetricsQueryService;
import com.paganbit.telaio.metrics.store.DalMetricsStore;
import com.paganbit.telaio.metrics.store.InMemoryDalMetricsStore;
import com.paganbit.telaio.metrics.store.jdbc.JdbcDalMetricsStore;
import com.turkraft.springfilter.parser.node.FilterNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the metrics autoconfiguration end to end without telaio-web: DALs are invoked
 * programmatically, the core interception proxy records the call, and a flush makes the data
 * queryable through the store.
 */
class TelaioMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            TelaioCoreAutoConfiguration.class, TelaioMetricsAutoConfiguration.class))
        .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new);

    @Test
    void byDefault_withoutDataSource_shouldUseInMemoryStore() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DalMetricsInterceptorProvider.class);
            assertThat(context).hasSingleBean(DalMetricsFlushScheduler.class);
            assertThat(context.getBean(DalMetricsStore.class)).isInstanceOf(InMemoryDalMetricsStore.class);
            assertThat(context.getBean(DalMetricsQueryService.class))
                .isInstanceOf(InMemoryDalMetricsStore.class);
        });
    }

    @Test
    void disabled_shouldRegisterNoMetricsBeans() {
        contextRunner.withPropertyValues("telaio.metrics.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DalMetricsBucketMerger.class);
            assertThat(context).doesNotHaveBean(DalMetricsAggregator.class);
            assertThat(context).doesNotHaveBean(DalMetricsInterceptorProvider.class);
            assertThat(context).doesNotHaveBean(DalMetricsStore.class);
            assertThat(context).doesNotHaveBean(DalMetricsQueryService.class);
            assertThat(context).doesNotHaveBean(DalMetricsFlushScheduler.class);
        });
    }

    @Test
    void withDataSource_shouldUseJdbcStore() {
        contextRunner
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withPropertyValues("telaio.metrics.jdbc.initialize-schema=always")
            .run(context -> {
                assertThat(context.getBean(DalMetricsStore.class)).isInstanceOf(JdbcDalMetricsStore.class);
                assertThat(context).doesNotHaveBean(InMemoryDalMetricsStore.class);
            });
    }

    @Test
    void micrometerEnabled_withMeterRegistry_shouldSupersedeInHousePath() {
        contextRunner
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues("telaio.metrics.micrometer.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(MicrometerDalMetricsRecorder.class);
                assertThat(context.getBean(DalMetricsRecorder.class))
                    .isInstanceOf(MicrometerDalMetricsRecorder.class);
                // in-house path stands aside
                assertThat(context).doesNotHaveBean(DalMetricsAggregator.class);
                assertThat(context).doesNotHaveBean(DalMetricsStore.class);
                assertThat(context).doesNotHaveBean(DalMetricsFlushScheduler.class);
                // the interceptor provider stays, fed by the Micrometer recorder
                assertThat(context).hasSingleBean(DalMetricsInterceptorProvider.class);
            });
    }

    @Test
    void micrometerEnabled_withInHouseForced_shouldRunBothPaths() {
        contextRunner
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues(
                "telaio.metrics.micrometer.enabled=true",
                "telaio.metrics.in-house.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(MicrometerDalMetricsRecorder.class);
                assertThat(context).hasSingleBean(DalMetricsAggregator.class);
                assertThat(context).hasSingleBean(DalMetricsStore.class);
                assertThat(context).hasSingleBean(DalMetricsFlushScheduler.class);
                assertThat(context.getBeansOfType(DalMetricsRecorder.class)).hasSize(2);
            });
    }

    @Test
    void micrometerEnabled_withoutMicrometerOnClasspath_shouldKeepInHousePath() {
        contextRunner
            .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
            .withPropertyValues("telaio.metrics.micrometer.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(DalMetricsAggregator.class);
                assertThat(context).doesNotHaveBean(MicrometerDalMetricsRecorder.class);
                assertThat(context.getBean(DalMetricsStore.class))
                    .isInstanceOf(InMemoryDalMetricsStore.class);
            });
    }

    @Test
    void withoutSpringJdbc_shouldFallBackToInMemoryStore() {
        contextRunner
            .withClassLoader(new FilteredClassLoader(JdbcTemplate.class))
            .run(context -> assertThat(context.getBean(DalMetricsStore.class))
                .isInstanceOf(InMemoryDalMetricsStore.class));
    }

    @Test
    void propertyBinding_shouldDriveBucketAndFlushDurations() {
        contextRunner
            .withPropertyValues(
                "telaio.metrics.bucket-duration=10s",
                "telaio.metrics.histogram.bucket-count=8")
            .run(context -> {
                TelaioMetricsProperties properties = context.getBean(TelaioMetricsProperties.class);
                assertThat(properties.getBucketDuration()).isEqualTo(Duration.ofSeconds(10));
                assertThat(properties.getHistogram().getBucketCount()).isEqualTo(8);
            });
    }

    @Test
    void metricsDal_invokedProgrammatically_shouldBeRecordedAndQueryable() {
        contextRunner
            .withBean(MetricsStubDal.class)
            .run(context -> {
                MetricsStubDal dal = context.getBean(MetricsStubDal.class);
                assertThat(AopUtils.isCglibProxy(dal)).isTrue();

                dal.create(Map.of("name", "Widget"));
                dal.create(Map.of("name", "Gadget"));

                // stop() drains all, including the still-open current bucket — deterministic,
                // unlike a periodic flush that only drains completed windows
                context.getBean(DalMetricsFlushScheduler.class).stop();

                DalMetricsQueryService query = context.getBean(DalMetricsQueryService.class);
                // Fixed, far-future upper bound: deterministic, yet always covers the bucket the
                // production aggregator records at real wall-clock time during this test.
                DalMetricsStats stats = query.stats(
                    "metrics-stubs", DalOperationType.CREATE, Instant.EPOCH,
                    Instant.parse("2999-01-01T00:00:00Z"));
                assertThat(stats.count()).isEqualTo(2);
            });
    }

    @Test
    void dalWithMetricsDisabled_shouldNotBeProxied() {
        contextRunner
            .withBean(DisabledMetricsStubDal.class)
            .run(context -> {
                DisabledMetricsStubDal dal = context.getBean(DisabledMetricsStubDal.class);
                assertThat(AopUtils.isAopProxy(dal)).isFalse();
            });
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

    @DalService(name = "metrics-stubs")
    static class MetricsStubDal extends StubDal {
    }

    @DalService(name = "disabled-stubs")
    @com.paganbit.telaio.metrics.annotation.DalMetrics(enabled = false)
    static class DisabledMetricsStubDal extends StubDal {
    }

    @Test
    void withNoRecorders_shouldNotRegisterInterceptorProvider() {
        contextRunner
            .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
            .withPropertyValues("telaio.metrics.in-house.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(DalMetricsRecorder.class);
                assertThat(context).doesNotHaveBean(DalMetricsInterceptorProvider.class);
            });
    }

    @Test
    void endpointAutoconfiguration_shouldRegisterEndpointWhenExposed() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                TelaioCoreAutoConfiguration.class,
                TelaioMetricsAutoConfiguration.class,
                TelaioMetricsEndpointAutoConfiguration.class))
            .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new)
            .withPropertyValues("management.endpoints.web.exposure.include=telaiometrics")
            .run(context -> assertThat(context).hasSingleBean(TelaioMetricsEndpoint.class));
    }
}
