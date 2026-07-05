package io.paganbit.telaio.metrics.store;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.model.DalMetricsStats;
import io.paganbit.telaio.metrics.model.LatencyHistogramScale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDalMetricsStoreTest {

    private static final Instant T0 = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");

    private final LatencyHistogramScale scale = LatencyHistogramScale.of(Duration.ofMillis(1), 2.0, 4);
    private final DalMetricsBucketMerger merger = new DefaultDalMetricsBucketMerger(scale, List.of(0.5, 0.9, 0.95, 0.99));

    private InMemoryDalMetricsStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryDalMetricsStore(
            merger, Duration.ofHours(24), 10_000, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DalMetricsBucket bucket(
        Instant start, String dal, DalOperationType op, long count, long errors, long totalNanos) {
        // clientErrorCount = errors * 2: distinct from errorCount, so a counter swap fails merges.
        return new DalMetricsBucket(start, Duration.ofMinutes(1), dal, op, count, errors, errors * 2,
            totalNanos, totalNanos / Math.max(count, 1), totalNanos / Math.max(count, 1),
            new long[]{0, count, 0, 0});
    }

    @Test
    void store_shouldMergeBucketsWithTheSameKey() {
        store.store(List.of(bucket(T0, "products", DalOperationType.READ, 5, 1, 5_000_000)));
        store.store(List.of(bucket(T0, "products", DalOperationType.READ, 3, 0, 3_000_000)));

        DalMetricsStats stats = store.stats("products", DalOperationType.READ, T0, NOW);
        assertThat(stats.count()).isEqualTo(8);
        assertThat(stats.errorCount()).isEqualTo(1);
        assertThat(stats.clientErrorCount()).isEqualTo(2);
        assertThat(stats.totalDuration()).isEqualTo(Duration.ofNanos(8_000_000));
    }

    @Test
    void stats_acrossOperations_shouldMergeWhenOperationIsNull() {
        store.store(List.of(
            bucket(T0, "products", DalOperationType.READ, 5, 0, 5_000_000),
            bucket(T0, "products", DalOperationType.CREATE, 2, 0, 4_000_000)));

        DalMetricsStats stats = store.stats("products", null, T0, NOW);
        assertThat(stats.count()).isEqualTo(7);
        assertThat(stats.operation()).isNull();
    }

    @Test
    void statsByOperation_shouldSplitPerOperation() {
        store.store(List.of(
            bucket(T0, "products", DalOperationType.READ, 5, 0, 5_000_000),
            bucket(T0, "products", DalOperationType.CREATE, 2, 0, 4_000_000)));

        Map<DalOperationType, DalMetricsStats> byOp = store.statsByOperation("products", T0, NOW);
        assertThat(byOp).containsOnlyKeys(DalOperationType.READ, DalOperationType.CREATE);
        assertThat(byOp.get(DalOperationType.READ).count()).isEqualTo(5);
        assertThat(byOp.get(DalOperationType.CREATE).count()).isEqualTo(2);
    }

    @Test
    void dalNames_shouldReturnDistinctSortedNames() {
        store.store(List.of(
            bucket(T0, "products", DalOperationType.READ, 1, 0, 1_000_000),
            bucket(T0, "orders", DalOperationType.READ, 1, 0, 1_000_000),
            bucket(T0, "products", DalOperationType.CREATE, 1, 0, 1_000_000)));

        assertThat(store.dalNames(T0, NOW)).containsExactly("orders", "products");
    }

    @Test
    void topSlowest_shouldRankByMeanDescending() {
        // products/READ mean 2ms, orders/READ mean 5ms, products/CREATE mean 1ms
        store.store(List.of(
            bucket(T0, "products", DalOperationType.READ, 10, 0, 20_000_000),
            bucket(T0, "orders", DalOperationType.READ, 10, 0, 50_000_000),
            bucket(T0, "products", DalOperationType.CREATE, 10, 0, 10_000_000)));

        List<DalMetricsStats> ranked = store.topSlowest(2, T0, NOW);
        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).dalName()).isEqualTo("orders");
        assertThat(ranked.get(1).dalName()).isEqualTo("products");
        assertThat(ranked.get(1).operation()).isEqualTo(DalOperationType.READ);
    }

    @Test
    void range_shouldExcludeBucketsOutsideTheWindow() {
        Instant before = T0.minus(Duration.ofHours(1));
        store.store(List.of(
            bucket(before, "products", DalOperationType.READ, 100, 0, 100_000_000),
            bucket(T0, "products", DalOperationType.READ, 5, 0, 5_000_000)));

        DalMetricsStats stats = store.stats("products", DalOperationType.READ, T0, NOW);
        assertThat(stats.count()).isEqualTo(5);
    }

    @Test
    void store_shouldEvictBucketsOlderThanRetention() {
        Instant old = NOW.minus(Duration.ofHours(25));   // beyond 24h retention
        store.store(List.of(bucket(old, "products", DalOperationType.READ, 1, 0, 1_000_000)));

        assertThat(store.findBuckets(null, null, Instant.EPOCH, NOW)).isEmpty();
    }

    @Test
    void store_shouldEnforceMaxBucketsCap() {
        InMemoryDalMetricsStore capped = new InMemoryDalMetricsStore(
            merger, Duration.ofDays(365), 2, Clock.fixed(NOW, ZoneOffset.UTC));
        capped.store(List.of(
            bucket(T0, "a", DalOperationType.READ, 1, 0, 1_000_000),
            bucket(T0.plusSeconds(60), "b", DalOperationType.READ, 1, 0, 1_000_000),
            bucket(T0.plusSeconds(120), "c", DalOperationType.READ, 1, 0, 1_000_000)));

        List<DalMetricsBucket> remaining = capped.findBuckets(null, null, Instant.EPOCH, NOW);
        assertThat(remaining).hasSize(2);
        // The oldest (a) was dropped, b and c kept
        assertThat(remaining).extracting(DalMetricsBucket::dalName).containsExactly("b", "c");
    }

    @Test
    void stats_withNoData_shouldReturnZeros() {
        DalMetricsStats stats = store.stats("missing", DalOperationType.READ, T0, NOW);
        assertThat(stats.count()).isZero();
        assertThat(stats.mean()).isEqualTo(Duration.ZERO);
    }
}
