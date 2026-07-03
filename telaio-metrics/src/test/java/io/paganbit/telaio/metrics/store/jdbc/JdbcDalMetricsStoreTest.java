package io.paganbit.telaio.metrics.store.jdbc;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.model.DalMetricsStats;
import io.paganbit.telaio.metrics.model.LatencyHistogramScale;
import io.paganbit.telaio.metrics.store.DalMetricsBucketMerger;
import io.paganbit.telaio.metrics.store.DefaultDalMetricsBucketMerger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDalMetricsStoreTest {

    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final LatencyHistogramScale scale = LatencyHistogramScale.of(Duration.ofMillis(1), 2.0, 4);
    private final DalMetricsBucketMerger merger = new DefaultDalMetricsBucketMerger(scale, List.of(0.5, 0.9, 0.95, 0.99));

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private JdbcDalMetricsStore store;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        jdbcTemplate = new JdbcTemplate(database);

        TelaioMetricsProperties properties = new TelaioMetricsProperties();
        properties.getJdbc().setInitializeSchema(TelaioMetricsProperties.Jdbc.SchemaInitialization.ALWAYS);
        new JdbcDalMetricsSchemaInitializer(database, properties.getJdbc()).initializeDatabase();

        store = new JdbcDalMetricsStore(
            jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofDays(7), null,
            Duration.ofHours(1), FIXED_CLOCK);
    }

    /**
     * A clock whose instant can be advanced, for deterministic testing of the cleanup throttle.
     */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    private DalMetricsBucket bucket(
        Instant start, String dal, DalOperationType op, long count, long errors, long totalNanos) {
        return new DalMetricsBucket(start, Duration.ofMinutes(1), dal, op, count, errors,
            totalNanos, totalNanos / Math.max(count, 1), totalNanos / Math.max(count, 1),
            new long[]{0, count, 0, 0});
    }

    @Test
    void schemaInitializer_shouldCreateTheTable() {
        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void store_thenQuery_shouldRoundTrip() {
        store.store(List.of(bucket(T0, "products", DalOperationType.READ, 5, 1, 5_000_000)));

        DalMetricsStats stats = store.stats("products", DalOperationType.READ, T0, NOW);
        assertThat(stats.count()).isEqualTo(5);
        assertThat(stats.errorCount()).isEqualTo(1);
        assertThat(stats.totalDuration()).isEqualTo(Duration.ofNanos(5_000_000));
        assertThat(stats.percentiles()).containsKey(0.95);
    }

    @Test
    void store_sameKeyTwice_shouldMergeViaDuplicateKeyFallback() {
        store.store(List.of(bucket(T0, "products", DalOperationType.READ, 5, 0, 5_000_000)));
        // Same instance, same key → INSERT collides, read-merge-update path runs
        store.store(List.of(bucket(T0, "products", DalOperationType.READ, 3, 1, 3_000_000)));

        DalMetricsStats stats = store.stats("products", DalOperationType.READ, T0, NOW);
        assertThat(stats.count()).isEqualTo(8);
        assertThat(stats.errorCount()).isEqualTo(1);
        assertThat(stats.totalDuration()).isEqualTo(Duration.ofNanos(8_000_000));

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void dalNames_shouldReturnDistinctSorted() {
        store.store(List.of(
            bucket(T0, "products", DalOperationType.READ, 1, 0, 1_000_000),
            bucket(T0, "orders", DalOperationType.READ, 1, 0, 1_000_000)));

        assertThat(store.dalNames(T0, NOW)).containsExactly("orders", "products");
    }

    @Test
    void statsByOperation_shouldSplitPerOperation() {
        store.store(List.of(
            bucket(T0, "products", DalOperationType.READ, 5, 0, 5_000_000),
            bucket(T0, "products", DalOperationType.CREATE, 2, 0, 4_000_000)));

        Map<DalOperationType, DalMetricsStats> byOp = store.statsByOperation("products", T0, NOW);
        assertThat(byOp).containsOnlyKeys(DalOperationType.READ, DalOperationType.CREATE);
        assertThat(byOp.get(DalOperationType.READ).count()).isEqualTo(5);
    }

    @Test
    void topSlowest_shouldRankByMeanDescending() {
        store.store(List.of(
            bucket(T0, "products", DalOperationType.READ, 10, 0, 20_000_000),
            bucket(T0, "orders", DalOperationType.READ, 10, 0, 50_000_000)));

        List<DalMetricsStats> ranked = store.topSlowest(5, T0, NOW);
        assertThat(ranked).extracting(DalMetricsStats::dalName).containsExactly("orders", "products");
    }

    @Test
    void findBuckets_shouldFilterByRange() {
        Instant before = T0.minus(Duration.ofHours(1));
        store.store(List.of(
            bucket(before, "products", DalOperationType.READ, 100, 0, 100_000_000),
            bucket(T0, "products", DalOperationType.READ, 5, 0, 5_000_000)));

        assertThat(store.findBuckets("products", DalOperationType.READ, T0, NOW)).hasSize(1);
    }

    @Test
    void store_shouldDeleteExpiredBuckets() {
        // cleanupInterval = ZERO → cleanup is due on every store(). One store() inserts both an
        // expired and a fresh row; retention = 1h keeps the fresh row well clear of the cutoff.
        JdbcDalMetricsStore shortRetention = new JdbcDalMetricsStore(
            jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofHours(1), null,
            Duration.ZERO, FIXED_CLOCK);
        Instant old = NOW.minus(Duration.ofDays(2));
        shortRetention.store(List.of(
            bucket(old, "expiring", DalOperationType.READ, 1, 0, 1_000_000),
            bucket(NOW, "fresh", DalOperationType.CREATE, 1, 0, 1_000_000)));

        // Exactly one row remains, and it is the fresh one — proving the expired row was deleted
        // (not merely missed by a precision-sensitive timestamp match).
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT dal_name FROM telaio_metrics_bucket", String.class)).isEqualTo("fresh");
    }

    @Test
    void cleanup_throttledWithinInterval_shouldNotDelete() {
        // cleanupInterval = 1h, lastCleanup initialized to construction time → not due on the
        // immediate follow-up store(), so the expired row survives (2 distinct rows remain).
        JdbcDalMetricsStore throttled = new JdbcDalMetricsStore(
            jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofMillis(1), null,
            Duration.ofHours(1), FIXED_CLOCK);
        Instant old = NOW.minus(Duration.ofDays(2));
        throttled.store(List.of(bucket(old, "products", DalOperationType.READ, 1, 0, 1_000_000)));
        throttled.store(List.of(
            bucket(NOW, "products", DalOperationType.CREATE, 1, 0, 1_000_000)));

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class);
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void cleanup_afterInterval_shouldDelete() {
        MutableClock clock = new MutableClock(NOW);
        JdbcDalMetricsStore throttled = new JdbcDalMetricsStore(
            jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofMillis(1), null,
            Duration.ofHours(1), clock);
        Instant old = clock.instant().minus(Duration.ofDays(2));
        throttled.store(List.of(bucket(old, "products", DalOperationType.READ, 1, 0, 1_000_000)));

        // Cross the cleanup interval, then store again → cleanup is now due and evicts the old row,
        // leaving only the freshly stored one.
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        throttled.store(List.of(
            bucket(clock.instant(), "products", DalOperationType.CREATE, 1, 0, 1_000_000)));

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void store_sameKeyTwice_withTransactionTemplate_shouldMergeAtomically() {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(database));
        JdbcDalMetricsStore transactionalStore = new JdbcDalMetricsStore(
            jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofDays(7), tx,
            Duration.ofHours(1), FIXED_CLOCK);

        transactionalStore.store(List.of(bucket(T0, "products", DalOperationType.READ, 5, 0, 5_000_000)));
        // Same instance, same key → INSERT collides, read-merge-update runs inside a transaction
        transactionalStore.store(List.of(bucket(T0, "products", DalOperationType.READ, 3, 1, 3_000_000)));

        DalMetricsStats stats = transactionalStore.stats("products", DalOperationType.READ, T0, NOW);
        assertThat(stats.count()).isEqualTo(8);
        assertThat(stats.errorCount()).isEqualTo(1);
        assertThat(stats.totalDuration()).isEqualTo(Duration.ofNanos(8_000_000));

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void crossInstanceRows_shouldMergeAtQueryTime() {
        // Two stores = two instances writing the same logical bucket as separate rows
        JdbcDalMetricsStore instanceA = new JdbcDalMetricsStore(
            jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofDays(7), null,
            Duration.ofHours(1), FIXED_CLOCK);
        JdbcDalMetricsStore instanceB = new JdbcDalMetricsStore(
            jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofDays(7), null,
            Duration.ofHours(1), FIXED_CLOCK);
        instanceA.store(List.of(bucket(T0, "products", DalOperationType.READ, 5, 0, 5_000_000)));
        instanceB.store(List.of(bucket(T0, "products", DalOperationType.READ, 7, 1, 7_000_000)));

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM telaio_metrics_bucket", Integer.class);
        assertThat(rows).isEqualTo(2);

        DalMetricsStats stats = instanceA.stats("products", DalOperationType.READ, T0, NOW);
        assertThat(stats.count()).isEqualTo(12);
        assertThat(stats.errorCount()).isEqualTo(1);
    }
}
