package com.paganbit.telaio.metrics.autoconfigure;

import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.metrics.collector.*;
import com.paganbit.telaio.metrics.model.LatencyHistogramScale;
import com.paganbit.telaio.metrics.store.DalMetricsBucketMerger;
import com.paganbit.telaio.metrics.store.DalMetricsStore;
import com.paganbit.telaio.metrics.store.DefaultDalMetricsBucketMerger;
import com.paganbit.telaio.metrics.store.InMemoryDalMetricsStore;
import com.paganbit.telaio.metrics.store.jdbc.JdbcDalMetricsSchemaInitializer;
import com.paganbit.telaio.metrics.store.jdbc.JdbcDalMetricsStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio Metrics.
 *
 * <p>Registers the collection pipeline — the channel-agnostic interceptor provider plus, by
 * default, the in-house path: aggregator, flush scheduler, and a metrics store. When a
 * {@link DataSource} and Spring JDBC are present, the JDBC store is chosen; otherwise the bounded
 * in-memory store is the fallback so the {@code telaiometrics} endpoint works with zero
 * configuration. Metrics collection is on by default and disabled globally with
 * {@code telaio.metrics.enabled=false}.</p>
 *
 * <p>Set {@code telaio.metrics.micrometer.enabled=true} (with Micrometer on the classpath and a
 * {@link MeterRegistry} bean available) to record into Micrometer instead.
 * The Micrometer recorder then <em>supersedes</em>
 * the in-house path ({@link OnInHouseMetricsCondition}); set
 * {@code telaio.metrics.in-house.enabled=true} to run both during a transition.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@AutoConfiguration(
    after = TelaioCoreAutoConfiguration.class,
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
)
@ConditionalOnProperty(name = "telaio.metrics.enabled", matchIfMissing = true)
@EnableConfigurationProperties(TelaioMetricsProperties.class)
public class TelaioMetricsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TelaioMetricsAutoConfiguration.class);

    @Bean
    @Conditional(OnInHouseMetricsCondition.class)
    @ConditionalOnMissingBean
    DalMetricsBucketMerger dalMetricsBucketMerger(TelaioMetricsProperties properties) {
        return new DefaultDalMetricsBucketMerger(
            histogramScale(properties),
            properties.getHistogram().getQuantiles()
        );
    }

    @Bean
    @Conditional(OnInHouseMetricsCondition.class)
    @ConditionalOnMissingBean
    DalMetricsAggregator dalMetricsAggregator(TelaioMetricsProperties properties) {
        return new DefaultDalMetricsAggregator(
            histogramScale(properties),
            properties.getBucketDuration(),
            Clock.systemUTC()
        );
    }

    /**
     * Builds the latency histogram scale from the configured properties. The scale is an internal
     * value object derived entirely from {@code telaio.metrics.histogram.*}, not an overridable
     * bean — customize it through those properties, or replace the merger and aggregator beans.
     */
    private static LatencyHistogramScale histogramScale(TelaioMetricsProperties properties) {
        final var histogramProperties = properties.getHistogram();
        return LatencyHistogramScale.of(
            histogramProperties.getFirstUpperBound(),
            histogramProperties.getGrowthFactor(),
            histogramProperties.getBucketCount()
        );
    }

    /**
     * Contributes the metrics interceptor, fanning each measurement out to every available
     * {@link DalMetricsRecorder} (the in-house aggregator and/or the Micrometer recorder). With no
     * recorder active, the provider contributes nothing, so DALs incur no overhead.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DalMetricsRecorder.class)
    DalMetricsInterceptorProvider dalMetricsInterceptorProvider(List<DalMetricsRecorder> recorders) {
        return new DalMetricsInterceptorProvider(recorders);
    }

    @Bean
    @Conditional(OnInHouseMetricsCondition.class)
    @ConditionalOnMissingBean
    DalMetricsFlushScheduler dalMetricsFlushScheduler(
        DalMetricsAggregator aggregator,
        List<DalMetricsStore> stores,
        TelaioMetricsProperties properties
    ) {
        return new DefaultDalMetricsFlushScheduler(aggregator, stores, properties.getFlushInterval());
    }

    /**
     * In-memory fallback store, used only when no other {@link DalMetricsStore} (e.g., the JDBC
     * store below) has been registered.
     */
    @Bean
    @Conditional(OnInHouseMetricsCondition.class)
    @ConditionalOnMissingBean(DalMetricsStore.class)
    InMemoryDalMetricsStore inMemoryDalMetricsStore(
        DalMetricsBucketMerger merger,
        TelaioMetricsProperties properties
    ) {
        final var inMemoryProperties = properties.getInMemory();
        final var retention = inMemoryProperties.getRetention();
        final var maxBuckets = inMemoryProperties.getMaxBuckets();
        return new InMemoryDalMetricsStore(merger, retention, maxBuckets, Clock.systemUTC());
    }

    /**
     * JDBC store, selected when Spring JDBC and a {@link DataSource} are available. Nested
     * configuration classes are processed before the enclosing class's bean methods, so this
     * store is registered ahead of — and wins over — the in-memory fallback above.
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(OnInHouseMetricsCondition.class)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(name = "telaio.metrics.jdbc.enabled", matchIfMissing = true)
    static class JdbcStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(DalMetricsStore.class)
        JdbcDalMetricsStore jdbcDalMetricsStore(
            DataSource dataSource,
            DalMetricsBucketMerger merger,
            TelaioMetricsProperties properties,
            ObjectProvider<PlatformTransactionManager> txManagerProvider
        ) {
            final var jdbcProperties = properties.getJdbc();
            final var jdbcTemplate = new JdbcTemplate(dataSource);
            final var tableName = jdbcProperties.getTableName();
            final var retention = jdbcProperties.getRetention();
            final var txManager = txManagerProvider.getIfAvailable();
            final var tx = txManager != null ? new TransactionTemplate(txManager) : null;
            final var cleanupInterval = jdbcProperties.getCleanupInterval();
            return new JdbcDalMetricsStore(
                jdbcTemplate, merger, tableName, retention, tx, cleanupInterval, Clock.systemUTC());
        }

        @Bean
        @ConditionalOnMissingBean
        JdbcDalMetricsSchemaInitializer jdbcDalMetricsSchemaInitializer(
            DataSource dataSource,
            TelaioMetricsProperties properties
        ) {
            return new JdbcDalMetricsSchemaInitializer(dataSource, properties.getJdbc());
        }
    }

    /**
     * Micrometer recorder, opt-in via {@code telaio.metrics.micrometer.enabled=true}. Activation is
     * deliberately a property, not mere classpath presence: Spring Boot auto-creates a
     * {@code SimpleMeterRegistry} whenever Micrometer is present, so keying off presence would
     * hijack apps that use Micrometer for unrelated metrics. When enabled it records into the
     * application's {@link MeterRegistry} and, by default, the in-house path stands aside
     * ({@link OnInHouseMetricsCondition}).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(name = "telaio.metrics.micrometer.enabled", havingValue = "true")
    static class MicrometerRecorderConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @Nullable
        MicrometerDalMetricsRecorder micrometerDalMetricsRecorder(
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            TelaioMetricsProperties properties
        ) {
            MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
            if (meterRegistry == null) {
                log.warn("telaio.metrics.micrometer.enabled=true but no MeterRegistry bean is "
                    + "available; DAL metrics will not be recorded. Add a Micrometer registry "
                    + "implementation for your monitoring backend, or set the property back to false.");
                return null;
            }
            return new MicrometerDalMetricsRecorder(
                meterRegistry, properties.getMicrometer().getMetricName());
        }
    }
}
