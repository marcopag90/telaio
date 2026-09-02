package com.paganbit.telaio.metrics.autoconfigure;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.metrics.annotation.TelaioMetricsDataSource;
import com.paganbit.telaio.metrics.annotation.TelaioMetricsTransactionManager;
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
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the metrics autoconfiguration end to end without telaio-web: DALs are invoked
 * programmatically, the core interception proxy records the call, and a flush makes the data
 * queryable through the store.
 */
class TelaioMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        // Boot's Jackson autoconfiguration ships with telaio-core and provides the ObjectMapper bean
        // core's path resolver requires; the DalValidator bean requires a SpringValidatorAdapter.
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class, TelaioCoreAutoConfiguration.class,
            TelaioMetricsAutoConfiguration.class))
        .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new)
        .withBean(SpringValidatorAdapter.class,
            () -> new SpringValidatorAdapter(mock(jakarta.validation.Validator.class)));

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

    /**
     * Regression for the mixed-backend case: two default-candidate transaction managers (e.g. JPA +
     * Mongo) must not break the JDBC store, which builds its own manager instead of looking one up.
     */
    @Test
    void withTwoTransactionManagers_shouldStillRegisterJdbcStoreWithItsOwnManager() {
        contextRunner
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(TwoTransactionManagers.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                JdbcDalMetricsStore store = context.getBean(JdbcDalMetricsStore.class);
                assertThat(transactionManagerOf(store))
                    .isInstanceOf(JdbcTransactionManager.class)
                    .isNotIn(context.getBeansOfType(TransactionManager.class).values());
                assertThat(((JdbcTransactionManager) transactionManagerOf(store)).getDataSource())
                    .isSameAs(context.getBean(DataSource.class));
                // The private manager is not a bean: the application's transaction wiring is untouched.
                assertThat(context.getBeansOfType(TransactionManager.class)).hasSize(2);
            });
    }

    @Test
    void withSingleDataSource_shouldUsePrivateJdbcTransactionManagerAndRegisterNoTransactionManagerBean() {
        contextRunner
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .run(context -> {
                JdbcDalMetricsStore store = context.getBean(JdbcDalMetricsStore.class);
                assertThat(transactionManagerOf(store)).isInstanceOf(JdbcTransactionManager.class);
                assertThat(context).doesNotHaveBean(TransactionManager.class);
            });
    }

    @Test
    void withMarkedTransactionManager_shouldUseIt() {
        contextRunner
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(MarkedTransactionManager.class)
            .run(context -> {
                JdbcDalMetricsStore store = context.getBean(JdbcDalMetricsStore.class);
                assertThat(transactionManagerOf(store)).isSameAs(context.getBean("metricsTransactionManager"));
            });
    }

    /**
     * The marked DataSource is declared as a non-default candidate, so it stays invisible to by-type
     * autowiring while the qualifier still resolves it — for both the store and the schema initializer.
     */
    @Test
    void withTwoDataSources_markedOne_shouldHoldTheMetricsTable() {
        contextRunner
            .withUserConfiguration(TwoDataSourcesOneMarked.class)
            .withPropertyValues("telaio.metrics.jdbc.initialize-schema=always")
            .run(context -> {
                assertThat(context).hasNotFailed();
                DataSource metrics = context.getBean("metricsDataSource", DataSource.class);
                DataSource main = context.getBean("mainDataSource", DataSource.class);
                JdbcDalMetricsStore store = context.getBean(JdbcDalMetricsStore.class);
                assertThat(jdbcTemplateOf(store).getDataSource()).isSameAs(metrics);
                assertThat(((JdbcTransactionManager) transactionManagerOf(store)).getDataSource()).isSameAs(metrics);
                assertThat(tableExists(metrics)).as("schema created in the marked DataSource").isTrue();
                assertThat(tableExists(main)).as("main DataSource untouched").isFalse();
                // Plain by-type resolution still sees a single DataSource: the main one.
                assertThat(context.getBean(DataSource.class)).isSameAs(main);
            });
    }

    /**
     * The recommended recipe must also work when the marked DataSource is the application's only one:
     * {@code @ConditionalOnBean} would ignore a non-default candidate and silently leave the JDBC store
     * unregistered, so the presence guard is a custom condition that sees every DataSource definition.
     */
    @Test
    void withLoneMarkedNonDefaultDataSource_shouldStillActivateJdbcStore() {
        contextRunner
            .withUserConfiguration(LoneMarkedDataSource.class)
            .withPropertyValues("telaio.metrics.jdbc.initialize-schema=always")
            .run(context -> {
                assertThat(context).hasNotFailed();
                DataSource metrics = context.getBean("metricsDataSource", DataSource.class);
                assertThat(context).doesNotHaveBean(InMemoryDalMetricsStore.class);
                assertThat(jdbcTemplateOf(context.getBean(JdbcDalMetricsStore.class)).getDataSource()).isSameAs(metrics);
                assertThat(tableExists(metrics)).isTrue();
            });
    }

    @Test
    void withTwoDataSources_primaryOne_shouldUseThePrimary() {
        contextRunner
            .withUserConfiguration(TwoDataSourcesOnePrimary.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                JdbcDalMetricsStore store = context.getBean(JdbcDalMetricsStore.class);
                assertThat(jdbcTemplateOf(store).getDataSource())
                    .isSameAs(context.getBean("primaryDataSource", DataSource.class));
            });
    }

    @Test
    void withTwoDataSources_noneMarkedOrPrimary_shouldFailFastWithGuidance() {
        contextRunner
            .withUserConfiguration(TwoPlainDataSources.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("@TelaioMetricsDataSource")
                    .hasMessageContaining("firstDataSource")
                    .hasMessageContaining("secondDataSource");
            });
    }

    private static PlatformTransactionManager transactionManagerOf(JdbcDalMetricsStore store) {
        TransactionTemplate template =
            (TransactionTemplate) ReflectionTestUtils.getField(store, "transactionTemplate");
        assertThat(template).as("the autoconfigured store always carries a transaction template").isNotNull();
        return Objects.requireNonNull(template.getTransactionManager());
    }

    private static JdbcTemplate jdbcTemplateOf(JdbcDalMetricsStore store) {
        return Objects.requireNonNull((JdbcTemplate) ReflectionTestUtils.getField(store, "jdbcTemplate"));
    }

    private static boolean tableExists(DataSource dataSource) {
        try {
            new JdbcTemplate(dataSource)
                .queryForObject("select count(*) from telaio_metrics_bucket", Long.class);
            return true;
        } catch (DataAccessException notThere) {
            return false;
        }
    }

    private static DataSource h2() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoTransactionManagers {

        @Bean
        PlatformTransactionManager firstTransactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }

        @Bean
        PlatformTransactionManager secondTransactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MarkedTransactionManager {

        @Bean(defaultCandidate = false)
        @TelaioMetricsTransactionManager
        PlatformTransactionManager metricsTransactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDataSourcesOneMarked {

        @Bean
        DataSource mainDataSource() {
            return h2();
        }

        @Bean(defaultCandidate = false)
        @TelaioMetricsDataSource
        DataSource metricsDataSource() {
            return h2();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LoneMarkedDataSource {

        @Bean(defaultCandidate = false)
        @TelaioMetricsDataSource
        DataSource metricsDataSource() {
            return h2();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDataSourcesOnePrimary {

        @Bean
        @Primary
        DataSource primaryDataSource() {
            return h2();
        }

        @Bean
        DataSource otherDataSource() {
            return h2();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoPlainDataSources {

        @Bean
        DataSource firstDataSource() {
            return h2();
        }

        @Bean
        DataSource secondDataSource() {
            return h2();
        }
    }

    /**
     * Micrometer switched on without a {@code MeterRegistry} bean: the recorder factory warns and
     * yields no recorder, and the in-house path stands aside — DALs run unmeasured rather than failing.
     */
    @Test
    void micrometerEnabled_withoutMeterRegistry_shouldRegisterNoRecorder() {
        contextRunner
            .withPropertyValues("telaio.metrics.micrometer.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBeansOfType(MicrometerDalMetricsRecorder.class)).isEmpty();
                assertThat(context.getBeansOfType(DalMetricsRecorder.class)).isEmpty();
                assertThat(context).doesNotHaveBean(DalMetricsAggregator.class);
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
            // Hide the whole spring-jdbc package: the JDBC configuration also references
            // JdbcTransactionManager, which must stay behind the @ConditionalOnClass guard.
            .withClassLoader(new FilteredClassLoader("org.springframework.jdbc"))
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
                JacksonAutoConfiguration.class,
                TelaioCoreAutoConfiguration.class,
                TelaioMetricsAutoConfiguration.class,
                TelaioMetricsEndpointAutoConfiguration.class))
            .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new)
            .withBean(SpringValidatorAdapter.class,
                () -> new SpringValidatorAdapter(mock(jakarta.validation.Validator.class)))
            .withPropertyValues("management.endpoints.web.exposure.include=telaiometrics")
            .run(context -> assertThat(context).hasSingleBean(TelaioMetricsEndpoint.class));
    }
}
