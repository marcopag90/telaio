package com.paganbit.telaio.metrics.model;

import com.paganbit.telaio.core.adapter.DalOperationType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DalMetricsBucketTest {

    private static final Instant START = Instant.parse("2026-06-12T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(1);

    // --- equals / hashCode / toString ---

    @Test
    void equals_shouldConsiderHistogramContent() {
        assertThat(bucket(new long[]{1, 2, 3, 0}))
            .isEqualTo(bucket(new long[]{1, 2, 3, 0}));
    }

    @Test
    void equals_shouldReturnFalseForDifferentHistogramContent() {
        assertThat(bucket(new long[]{1, 2, 3, 0}))
            .isNotEqualTo(bucket(new long[]{1, 2, 4, 0}));
    }

    @Test
    void equals_shouldBeReflexiveAndRejectOtherTypesAndNull() {
        DalMetricsBucket bucket = bucket(new long[]{1, 2, 3, 0});
        assertThat(bucket)
            .isNotEqualTo(null);
    }

    @Test
    void equals_shouldConsiderEveryComponent() {
        long[] histogram = {1, 2, 3, 0};
        DalMetricsBucket reference = new DalMetricsBucket(
            START, WINDOW, "products", DalOperationType.READ, 10, 1, 2, 5_000, 100, 900, histogram);

        assertThat(reference)
            // bucketStart
            .isNotEqualTo(new DalMetricsBucket(
                START.plusSeconds(60), WINDOW, "products", DalOperationType.READ, 10, 1, 2, 5_000, 100, 900, histogram))
            // bucketDuration
            .isNotEqualTo(new DalMetricsBucket(
                START, Duration.ofMinutes(5), "products", DalOperationType.READ, 10, 1, 2, 5_000, 100, 900, histogram))
            // dalName
            .isNotEqualTo(new DalMetricsBucket(
                START, WINDOW, "orders", DalOperationType.READ, 10, 1, 2, 5_000, 100, 900, histogram))
            // operation
            .isNotEqualTo(new DalMetricsBucket(
                START, WINDOW, "products", DalOperationType.CREATE, 10, 1, 2, 5_000, 100, 900, histogram))
            // count
            .isNotEqualTo(new DalMetricsBucket(
                START, WINDOW, "products", DalOperationType.READ, 11, 1, 2, 5_000, 100, 900, histogram))
            // errorCount
            .isNotEqualTo(new DalMetricsBucket(
                START, WINDOW, "products", DalOperationType.READ, 10, 2, 2, 5_000, 100, 900, histogram))
            // totalDurationNanos
            .isNotEqualTo(new DalMetricsBucket(
                START, WINDOW, "products", DalOperationType.READ, 10, 1, 2, 6_000, 100, 900, histogram))
            // minDurationNanos
            .isNotEqualTo(new DalMetricsBucket(
                START, WINDOW, "products", DalOperationType.READ, 10, 1, 2, 5_000, 50, 900, histogram))
            // maxDurationNanos
            .isNotEqualTo(new DalMetricsBucket(
                START, WINDOW, "products", DalOperationType.READ, 10, 1, 2, 5_000, 100, 950, histogram))
            // same content, distinct histogram array instance
            .isEqualTo(new DalMetricsBucket(
                START, WINDOW, "products", DalOperationType.READ, 10, 1, 2, 5_000, 100, 900, histogram.clone()));
    }

    @Test
    void hashCode_shouldBeConsistentWithEquals() {
        assertThat(bucket(new long[]{1, 2, 3, 0}))
            .hasSameHashCodeAs(bucket(new long[]{1, 2, 3, 0}));
    }

    @Test
    void toString_shouldIncludeHistogramValues() {
        assertThat(bucket(new long[]{1, 2, 3, 0}).toString()).contains("[1, 2, 3, 0]");
    }

    // --- add ---

    @Test
    void add_shouldSumCountsAndErrors() {
        DalMetricsBucket result = bucketWith(10, 1, 2, 0, 0, 0, new long[]{5, 5, 0, 0})
            .add(bucketWith(20, 3, 1, 0, 0, 0, new long[]{10, 5, 5, 0}));

        assertThat(result.count()).isEqualTo(30);
        assertThat(result.errorCount()).isEqualTo(4);
        assertThat(result.clientErrorCount()).isEqualTo(3);
    }

    @Test
    void equals_shouldConsiderClientErrorCount() {
        assertThat(bucketWith(10, 1, 2, 0, 0, 0, new long[]{5, 5, 0, 0}))
            .isNotEqualTo(bucketWith(10, 1, 3, 0, 0, 0, new long[]{5, 5, 0, 0}));
    }

    @Test
    void add_shouldSumTotalDuration() {
        DalMetricsBucket result = bucketWith(1, 0, 0, 1_000_000, 0, 0, new long[]{1, 0, 0, 0})
            .add(bucketWith(1, 0, 0, 2_000_000, 0, 0, new long[]{0, 1, 0, 0}));

        assertThat(result.totalDurationNanos()).isEqualTo(3_000_000);
    }

    @Test
    void add_shouldTakeMinOfMinDurations() {
        DalMetricsBucket result = bucketWith(1, 0, 0, 0, 5_000_000, 0, new long[]{1, 0, 0, 0})
            .add(bucketWith(1, 0, 0, 0, 2_000_000, 0, new long[]{1, 0, 0, 0}));

        assertThat(result.minDurationNanos()).isEqualTo(2_000_000);
    }

    @Test
    void add_shouldTakeMaxOfMaxDurations() {
        DalMetricsBucket result = bucketWith(1, 0, 0, 0, 0, 5_000_000, new long[]{0, 0, 1, 0})
            .add(bucketWith(1, 0, 0, 0, 0, 9_000_000, new long[]{0, 0, 0, 1}));

        assertThat(result.maxDurationNanos()).isEqualTo(9_000_000);
    }

    @Test
    void add_shouldAddHistogramElementWise() {
        DalMetricsBucket result = bucketWith(5, 0, 0, 0, 0, 0, new long[]{1, 2, 2, 0})
            .add(bucketWith(5, 0, 0, 0, 0, 0, new long[]{3, 1, 0, 1}));

        assertThat(result.histogramCounts()).containsExactly(4, 3, 2, 1);
    }

    @Test
    void add_shouldKeepThisHistogramOnLengthMismatch() {
        DalMetricsBucket result = bucketWith(5, 0, 0, 0, 0, 0, new long[]{1, 2, 2, 0})
            .add(bucketWith(5, 0, 0, 0, 0, 0, new long[]{3, 2}));

        assertThat(result.histogramCounts()).containsExactly(1, 2, 2, 0);
        assertThat(result.count()).isEqualTo(10);
    }

    @Test
    void add_shouldPreserveKey() {
        DalMetricsBucket result = bucket(new long[]{1, 0, 0, 0})
            .add(bucket(new long[]{0, 1, 0, 0}));

        assertThat(result.bucketStart()).isEqualTo(START);
        assertThat(result.bucketDuration()).isEqualTo(WINDOW);
        assertThat(result.dalName()).isEqualTo("products");
        assertThat(result.operation()).isEqualTo(DalOperationType.READ);
    }

    private static DalMetricsBucket bucket(long[] histogram) {
        return bucketWith(10, 0, 0, 10_000_000, 1_000_000, 5_000_000, histogram);
    }

    private static DalMetricsBucket bucketWith(
        long count, long errors, long clientErrors, long totalNanos, long minNanos, long maxNanos,
        long[] histogram
    ) {
        return new DalMetricsBucket(START, WINDOW, "products", DalOperationType.READ,
            count, errors, clientErrors, totalNanos, minNanos, maxNanos, histogram);
    }
}
