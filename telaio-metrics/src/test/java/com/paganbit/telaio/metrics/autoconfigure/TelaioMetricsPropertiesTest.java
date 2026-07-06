package com.paganbit.telaio.metrics.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default values and binding behavior of {@link TelaioMetricsProperties}, and
 * ensures that Bean Validation rejects obviously invalid configurations at context startup.
 */
class TelaioMetricsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class);

    @Configuration
    @EnableConfigurationProperties(TelaioMetricsProperties.class)
    static class PropertiesConfig {
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    @Test
    void defaults_shouldReflectDocumentedValues() {
        contextRunner.run(context -> {
            TelaioMetricsProperties p = context.getBean(TelaioMetricsProperties.class);

            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getBucketDuration()).isEqualTo(Duration.ofMinutes(1));
            assertThat(p.getFlushInterval()).isEqualTo(Duration.ofMinutes(1));

            assertThat(p.getHistogram().getFirstUpperBound()).isEqualTo(Duration.ofMillis(1));
            assertThat(p.getHistogram().getGrowthFactor()).isEqualTo(2.0);
            assertThat(p.getHistogram().getBucketCount()).isEqualTo(20);
            assertThat(p.getHistogram().getQuantiles()).containsExactly(0.5, 0.9, 0.95, 0.99);

            assertThat(p.getInMemory().getRetention()).isEqualTo(Duration.ofHours(24));
            assertThat(p.getInMemory().getMaxBuckets()).isEqualTo(10_000);

            assertThat(p.getJdbc().isEnabled()).isTrue();
            assertThat(p.getJdbc().getTableName()).isEqualTo("telaio_metrics_bucket");
            assertThat(p.getJdbc().getInitializeSchema())
                .isEqualTo(TelaioMetricsProperties.Jdbc.SchemaInitialization.EMBEDDED);
            assertThat(p.getJdbc().getRetention()).isEqualTo(Duration.ofDays(7));
            assertThat(p.getJdbc().getCleanupInterval()).isEqualTo(Duration.ofHours(1));
            assertThat(p.getJdbc().getPlatform()).isNull();

            assertThat(p.getEndpoint().getDefaultRange()).isEqualTo(Duration.ofHours(1));
            assertThat(p.getEndpoint().getTopN()).isEqualTo(10);
        });
    }

    // ── Custom binding ────────────────────────────────────────────────────────

    @Test
    void customProperties_shouldBindCorrectly() {
        contextRunner
            .withPropertyValues(
                "telaio.metrics.bucket-duration=30s",
                "telaio.metrics.flush-interval=15s",
                "telaio.metrics.histogram.bucket-count=10",
                "telaio.metrics.histogram.growth-factor=3.0",
                "telaio.metrics.in-memory.max-buckets=500",
                "telaio.metrics.jdbc.table-name=custom_table",
                "telaio.metrics.jdbc.initialize-schema=always",
                "telaio.metrics.jdbc.cleanup-interval=30m",
                "telaio.metrics.jdbc.platform=postgresql",
                "telaio.metrics.endpoint.top-n=5",
                "telaio.metrics.endpoint.default-range=2h"
            )
            .run(context -> {
                TelaioMetricsProperties p = context.getBean(TelaioMetricsProperties.class);

                assertThat(p.getBucketDuration()).isEqualTo(Duration.ofSeconds(30));
                assertThat(p.getFlushInterval()).isEqualTo(Duration.ofSeconds(15));
                assertThat(p.getHistogram().getBucketCount()).isEqualTo(10);
                assertThat(p.getHistogram().getGrowthFactor()).isEqualTo(3.0);
                assertThat(p.getInMemory().getMaxBuckets()).isEqualTo(500);
                assertThat(p.getJdbc().getTableName()).isEqualTo("custom_table");
                assertThat(p.getJdbc().getInitializeSchema())
                    .isEqualTo(TelaioMetricsProperties.Jdbc.SchemaInitialization.ALWAYS);
                assertThat(p.getJdbc().getCleanupInterval()).isEqualTo(Duration.ofMinutes(30));
                assertThat(p.getJdbc().getPlatform()).isEqualTo("postgresql");
                assertThat(p.getEndpoint().getTopN()).isEqualTo(5);
                assertThat(p.getEndpoint().getDefaultRange()).isEqualTo(Duration.ofHours(2));
            });
    }

    // ── Validation failures ───────────────────────────────────────────────────

    @Test
    void customQuantiles_shouldBindCorrectly() {
        contextRunner
            .withPropertyValues("telaio.metrics.histogram.quantiles=0.5,0.99")
            .run(context -> assertThat(context.getBean(TelaioMetricsProperties.class)
                .getHistogram().getQuantiles()).containsExactly(0.5, 0.99));
    }

    @Test
    void emptyQuantiles_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.histogram.quantiles=")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void zeroQuantile_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.histogram.quantiles=0.0,0.99")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void quantileAboveOne_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.histogram.quantiles=0.5,1.1")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void quantileEqualToOne_shouldBeValid() {
        contextRunner
            .withPropertyValues("telaio.metrics.histogram.quantiles=0.5,1.0")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void invalidBucketCount_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.histogram.bucket-count=1")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidGrowthFactor_equalToOne_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.histogram.growth-factor=1.0")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidGrowthFactor_belowOne_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.histogram.growth-factor=0.5")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void blankTableName_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.jdbc.table-name=")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void malformedTableName_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.jdbc.table-name=metrics; DROP TABLE x")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void schemaQualifiedTableName_shouldFailContextStartup() {
        // No schema prefix: the shipped DDL creates the table unqualified, so the schema is a
        // DataSource concern, not part of the table name.
        contextRunner
            .withPropertyValues("telaio.metrics.jdbc.table-name=metrics.telaio_metrics_bucket")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void zeroMaxBuckets_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.in-memory.max-buckets=0")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void negativeMaxBuckets_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.in-memory.max-buckets=-1")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void zeroTopN_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.metrics.endpoint.top-n=0")
            .run(context -> assertThat(context).hasFailed());
    }
}
