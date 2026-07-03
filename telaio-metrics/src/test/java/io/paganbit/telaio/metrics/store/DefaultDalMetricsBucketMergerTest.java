package io.paganbit.telaio.metrics.store;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.model.DalMetricsStats;
import io.paganbit.telaio.metrics.model.LatencyHistogramScale;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDalMetricsBucketMergerTest {

    private static final Instant FROM = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-12T11:00:00Z");

    private final LatencyHistogramScale scale = LatencyHistogramScale.of(Duration.ofMillis(1), 2.0, 4);
    private final DalMetricsBucketMerger merger = new DefaultDalMetricsBucketMerger(scale, List.of(0.5, 0.9, 0.95, 0.99));

    @Test
    void merge_shouldCombineScalarsAndHistograms() {
        DalMetricsBucket first = bucket(10, 1, 30_000_000, 1_000_000, 9_000_000,
            new long[]{5, 5, 0, 0});
        DalMetricsBucket second = bucket(20, 4, 50_000_000, 500_000, 12_000_000,
            new long[]{10, 5, 5, 0});

        DalMetricsStats stats = merger.merge("products", DalOperationType.READ, FROM, TO,
            List.of(first, second));

        assertThat(stats.count()).isEqualTo(30);
        assertThat(stats.errorCount()).isEqualTo(5);
        assertThat(stats.totalDuration()).isEqualTo(Duration.ofNanos(80_000_000));
        assertThat(stats.min()).isEqualTo(Duration.ofNanos(500_000));
        assertThat(stats.max()).isEqualTo(Duration.ofNanos(12_000_000));
        assertThat(stats.mean()).isEqualTo(Duration.ofNanos(80_000_000 / 30));
        assertThat(stats.percentiles()).containsKeys(0.5, 0.9, 0.95, 0.99);
        assertThat(stats.dalName()).isEqualTo("products");
        assertThat(stats.operation()).isEqualTo(DalOperationType.READ);
    }

    @Test
    void merge_shouldReturnZerosOnNoBuckets() {
        DalMetricsStats stats = merger.merge("products", null, FROM, TO, List.of());

        assertThat(stats.count()).isZero();
        assertThat(stats.errorCount()).isZero();
        assertThat(stats.min()).isEqualTo(Duration.ZERO);
        assertThat(stats.max()).isEqualTo(Duration.ZERO);
        assertThat(stats.mean()).isEqualTo(Duration.ZERO);
        assertThat(stats.percentiles()).isEmpty();
    }

    @Test
    void merge_shouldSkipMismatchedHistogramsForPercentilesButKeepScalars() {
        // Persisted before a scale change: 2 histogram buckets instead of 4
        DalMetricsBucket legacy = bucket(100, 0, 100_000_000, 1_000_000, 1_000_000,
            new long[]{100, 0});
        DalMetricsBucket current = bucket(10, 0, 20_000_000, 2_000_000, 2_000_000,
            new long[]{0, 10, 0, 0});

        DalMetricsStats stats = merger.merge("products", DalOperationType.READ, FROM, TO,
            List.of(legacy, current));

        assertThat(stats.count()).isEqualTo(110);
        // Percentiles reflect only the 10 samples with a matching histogram (bucket 1: 1ms..2ms)
        assertThat(stats.percentiles().get(0.99))
            .isBetween(Duration.ofMillis(1), Duration.ofMillis(2));
    }

    @Test
    void merge_shouldIgnoreEmptyBuckets() {
        DalMetricsBucket empty = bucket(0, 0, 0, Long.MAX_VALUE, Long.MIN_VALUE,
            new long[]{0, 0, 0, 0});
        DalMetricsBucket real = bucket(5, 0, 10_000_000, 2_000_000, 2_000_000,
            new long[]{0, 5, 0, 0});

        DalMetricsStats stats = merger.merge("products", DalOperationType.READ, FROM, TO,
            List.of(empty, real));

        assertThat(stats.count()).isEqualTo(5);
        assertThat(stats.min()).isEqualTo(Duration.ofNanos(2_000_000));
    }

    @Test
    void merge_shouldUseConfiguredQuantiles() {
        DalMetricsBucketMerger customMerger = new DefaultDalMetricsBucketMerger(scale, List.of(0.5, 0.99));
        DalMetricsBucket b = bucket(10, 0, 20_000_000, 1_000_000, 4_000_000, new long[]{5, 5, 0, 0});

        DalMetricsStats stats = customMerger.merge("products", DalOperationType.READ, FROM, TO, List.of(b));

        assertThat(stats.percentiles()).containsOnlyKeys(0.5, 0.99);
    }

    private static DalMetricsBucket bucket(
        long count, long errors, long totalNanos, long minNanos, long maxNanos, long[] histogram
    ) {
        return new DalMetricsBucket(FROM, Duration.ofMinutes(1), "products", DalOperationType.READ,
            count, errors, totalNanos, minNanos, maxNanos, histogram);
    }
}
