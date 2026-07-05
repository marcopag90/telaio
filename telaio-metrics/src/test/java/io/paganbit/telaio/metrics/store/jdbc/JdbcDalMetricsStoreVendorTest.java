package io.paganbit.telaio.metrics.store.jdbc;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.model.DalMetricsStats;
import io.paganbit.telaio.metrics.model.LatencyHistogramScale;
import io.paganbit.telaio.metrics.store.DalMetricsBucketMerger;
import io.paganbit.telaio.metrics.store.DefaultDalMetricsBucketMerger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Driver;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Verifies the per-vendor DDL scripts and the full store round-trip against every supported
 * database, each pinned to its documented minimum version. Requires Docker; the whole class
 * self-disables when Docker is absent.
 *
 * <p>Slow databases (notably Oracle) are intentionally included with no opt-out: the contract is
 * that every shipped {@code schema-*.sql} is exercised on a real engine on every build that has
 * Docker.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcDalMetricsStoreVendorTest {

    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final LatencyHistogramScale scale = LatencyHistogramScale.of(Duration.ofMillis(1), 2.0, 4);
    private final DalMetricsBucketMerger merger = new DefaultDalMetricsBucketMerger(scale, List.of(0.5, 0.9, 0.95, 0.99));

    static Stream<Arguments> supportedVendors() {
        return Stream.of(
            arguments("postgresql",
                (Supplier<JdbcDatabaseContainer<?>>) () -> new PostgreSQLContainer(image("postgresql"))),
            arguments("mysql",
                (Supplier<JdbcDatabaseContainer<?>>) () -> new MySQLContainer(image("mysql"))),
            arguments("mariadb",
                (Supplier<JdbcDatabaseContainer<?>>) () -> new MariaDBContainer(image("mariadb"))),
            arguments("sqlserver",
                (Supplier<JdbcDatabaseContainer<?>>) () ->
                    new MSSQLServerContainer(image("sqlserver")).acceptLicense()),
            arguments("oracle",
                (Supplier<JdbcDatabaseContainer<?>>) () -> new OracleContainer(image("oracle"))));
    }

    /**
     * Resolves the Docker image for a vendor from the {@code testcontainers.image.<vendor>} system
     * property, which the parent pom's surefire configuration supplies from a single set of Maven
     * properties. Keeping the coordinates out of the test code lets every module's vendor matrix
     * share the same pinned, documented versions.
     */
    private static String image(String vendor) {
        String image = System.getProperty("testcontainers.image." + vendor);
        if (image == null || image.isBlank()) {
            throw new IllegalStateException(
                "Missing system property 'testcontainers.image." + vendor + "'. Supported database "
                    + "images are defined in the parent pom.xml and passed through surefire — run "
                    + "these integration tests with Maven (e.g. mvn -pl telaio-metrics test).");
        }
        return image;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedVendors")
    void schemaAppliesAndStoreRoundTrips(
        String platform, Supplier<JdbcDatabaseContainer<?>> containerSupplier) throws Exception {
        try (JdbcDatabaseContainer<?> container = containerSupplier.get()) {
            container.start();
            DataSource dataSource = dataSourceFor(container);

            // Resolve the schema script from the platform name (matches the auto-detected driver)
            // and force creation regardless of embedded status.
            TelaioMetricsProperties properties = new TelaioMetricsProperties();
            properties.getJdbc().setPlatform(platform);
            properties.getJdbc().setInitializeSchema(TelaioMetricsProperties.Jdbc.SchemaInitialization.ALWAYS);
            final var initializer = new JdbcDalMetricsSchemaInitializer(dataSource, properties.getJdbc());
            final var executed = assertDoesNotThrow(initializer::initializeDatabase);
            assertThat(executed).isTrue();
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            JdbcDalMetricsStore store = new JdbcDalMetricsStore(
                jdbcTemplate,
                merger,
                "telaio_metrics_bucket",
                Duration.ofDays(7),
                null,
                Duration.ofHours(1),
                FIXED_CLOCK
            );

            store.store(List.of(bucket(T0, "products", DalOperationType.READ, 5, 1, 5_000_000)));
            // Re-store same key → duplicate-key fallback merge path (SELECT + UPDATE)
            store.store(List.of(bucket(T0, "products", DalOperationType.READ, 3, 0, 3_000_000)));
            store.store(List.of(bucket(T0, "orders", DalOperationType.CREATE, 2, 0, 4_000_000)));

            assertThat(store.dalNames(T0, NOW)).containsExactly("orders", "products");

            // stats() → findBuckets(dalName, operation)
            DalMetricsStats stats = store.stats("products", DalOperationType.READ, T0, NOW);
            assertThat(stats.count()).isEqualTo(8);
            assertThat(stats.errorCount()).isEqualTo(1);
            assertThat(stats.clientErrorCount()).isEqualTo(2);
            assertThat(stats.totalDuration()).isEqualTo(Duration.ofNanos(8_000_000));

            // statsByOperation() → findBuckets(dalName, null): dal filter, no operation filter
            Map<DalOperationType, DalMetricsStats> byOperation =
                store.statsByOperation("products", T0, NOW);
            assertThat(byOperation).containsOnlyKeys(DalOperationType.READ);
            assertThat(byOperation.get(DalOperationType.READ).count()).isEqualTo(8);

            // topSlowest() → findBuckets(null, null)
            List<DalMetricsStats> ranked = store.topSlowest(5, T0, NOW);
            assertThat(ranked).extracting(DalMetricsStats::dalName).contains("orders", "products");

            // deleteExpired() → DELETE actually evicts a row past the retention cutoff
            JdbcDalMetricsStore shortRetention = new JdbcDalMetricsStore(
                jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofMillis(1), null,
                Duration.ZERO, FIXED_CLOCK);
            Instant old = NOW.minus(Duration.ofDays(2));
            shortRetention.store(
                List.of(bucket(old, "expiring", DalOperationType.READ, 1, 0, 1_000_000)));
            // Retention delete runs on store(); a subsequent store of fresh data evicts the old row
            shortRetention.store(
                List.of(bucket(NOW, "fresh", DalOperationType.CREATE, 1, 0, 1_000_000)));
            Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM telaio_metrics_bucket WHERE bucket_start = ?", Long.class,
                Timestamp.from(old));
            assertThat(remaining).isZero();
        }
    }

    private DalMetricsBucket bucket(
        Instant start, String dal, DalOperationType op, long count, long errors, long totalNanos) {
        // clientErrorCount = errors * 2: distinct from errorCount, so a positional swap of the
        // two columns fails the vendor round-trip.
        return new DalMetricsBucket(start, Duration.ofMinutes(1), dal, op, count, errors, errors * 2,
            totalNanos, totalNanos / count, totalNanos / count, new long[]{0, count, 0, 0});
    }

    private static DataSource dataSourceFor(JdbcDatabaseContainer<?> container) throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends Driver> driverClass = (Class<? extends Driver>) Class.forName(container.getDriverClassName());
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(driverClass);
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUsername(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }
}
