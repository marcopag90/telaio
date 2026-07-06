package com.paganbit.telaio.metrics.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LatencyHistogramScaleTest {

    /**
     * Bounds: 1ms, 2ms, 4ms; bucket 3 is the overflow.
     */
    private final LatencyHistogramScale scale = LatencyHistogramScale.of(Duration.ofMillis(1), 2.0, 4);

    @Test
    void of_shouldGenerateGeometricBounds() {
        assertThat(scale.bucketCount()).isEqualTo(4);
        assertThat(scale.upperBoundsNanos())
            .containsExactly(1_000_000L, 2_000_000L, 4_000_000L);
    }

    @Test
    void of_shouldRejectInvalidSettings() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> LatencyHistogramScale.of(Duration.ZERO, 2.0, 4));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> LatencyHistogramScale.of(Duration.ofMillis(1), 1.0, 4));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> LatencyHistogramScale.of(Duration.ofMillis(1), 2.0, 1));
    }

    @Test
    void indexOf_shouldMapDurationsToBuckets() {
        assertThat(scale.indexOf(0)).isZero();
        assertThat(scale.indexOf(999_999)).isZero();
        assertThat(scale.indexOf(1_000_000)).isZero();           // exact bound belongs to its bucket
        assertThat(scale.indexOf(1_000_001)).isEqualTo(1);
        assertThat(scale.indexOf(4_000_000)).isEqualTo(2);
        assertThat(scale.indexOf(4_000_001)).isEqualTo(3);       // overflow
        assertThat(scale.indexOf(Long.MAX_VALUE)).isEqualTo(3);
    }

    @Test
    void percentile_shouldInterpolateWithinBucket() {
        // 10 samples in bucket 1 (1ms..2ms): p50 falls at rank 5 of 10 → midpoint 1.5ms
        long[] counts = {0, 10, 0, 0};

        assertThat(scale.percentile(counts, 0.5)).isEqualTo(Duration.ofNanos(1_500_000));
    }

    @Test
    void percentile_shouldSpanBuckets() {
        // 5 samples in bucket 0, 5 in bucket 2: p50 → end of bucket 0, p99 → inside bucket 2
        long[] counts = {5, 0, 5, 0};

        assertThat(scale.percentile(counts, 0.5)).isEqualTo(Duration.ofMillis(1));
        assertThat(scale.percentile(counts, 0.99))
            .isBetween(Duration.ofMillis(2), Duration.ofMillis(4));
    }

    @Test
    void percentile_shouldClampOverflowToLastFiniteBound() {
        long[] counts = {0, 0, 0, 10};

        assertThat(scale.percentile(counts, 0.99)).isEqualTo(Duration.ofMillis(4));
    }

    @Test
    void percentile_shouldReturnZeroOnEmptyHistogram() {
        long[] counts = {0, 0, 0, 0};

        assertThat(scale.percentile(counts, 0.5)).isEqualTo(Duration.ZERO);
    }

    @Test
    void percentile_shouldRejectMismatchedCountsAndQuantiles() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> scale.percentile(new long[]{1, 2}, 0.5));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> scale.percentile(new long[]{0, 0, 0, 0}, 0.0));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> scale.percentile(new long[]{0, 0, 0, 0}, 1.1));
    }

    @Test
    void of_shouldSaturateBoundsInsteadOfOverflowing() {
        LatencyHistogramScale wide = LatencyHistogramScale.of(Duration.ofMillis(1), 2.0, 80);

        long[] bounds = wide.upperBoundsNanos();
        assertThat(bounds[bounds.length - 1]).isEqualTo(Long.MAX_VALUE);
        for (int i = 1; i < bounds.length; i++) {
            assertThat(bounds[i]).isGreaterThanOrEqualTo(bounds[i - 1]);
        }
        // Long.MAX_VALUE lands in the first saturated bucket, never out of range
        assertThat(wide.indexOf(Long.MAX_VALUE)).isBetween(0, 79);
    }

    @Test
    void percentile_shouldRankExactlyAtBucketBoundary() {
        // Place samples to land exactly at the 50th percentile boundary (2ms)
        long[] counts = {5, 5, 0, 0};

        assertThat(scale.percentile(counts, 0.5)).isEqualTo(Duration.ofMillis(1));
    }

    @Test
    void percentile_p100_shouldReturnLastFiniteBound() {
        long[] counts = {1, 1, 1, 1};

        assertThat(scale.percentile(counts, 1.0)).isEqualTo(Duration.ofMillis(4));
    }

    @Test
    void percentile_shouldFocusOnSingleBucket() {
        // All samples in bucket 0 (0..1ms); p50 should be at 0.5ms
        long[] counts = {100, 0, 0, 0};

        assertThat(scale.percentile(counts, 0.5)).isEqualTo(Duration.ofNanos(500_000));
    }

    @Test
    void percentile_shouldInterpolateAllBuckets() {
        // Uniform distribution: 25 samples per bucket; p25, p50, p75, p99 should interpolate across buckets
        long[] counts = {25, 25, 25, 25};

        assertThat(scale.percentile(counts, 0.25)).isEqualTo(Duration.ofMillis(1));
        assertThat(scale.percentile(counts, 0.50)).isEqualTo(Duration.ofMillis(2));
        assertThat(scale.percentile(counts, 0.75)).isEqualTo(Duration.ofMillis(4));
        assertThat(scale.percentile(counts, 0.99)).isEqualTo(Duration.ofMillis(4));
    }

    @Test
    void percentile_shouldClampToLastBoundWhenNoInterpolationNeeded() {
        // Only the overflow bucket has samples; p99 should return the last finite bound
        long[] counts = {0, 0, 0, 50};

        assertThat(scale.percentile(counts, 0.99)).isEqualTo(Duration.ofMillis(4));
    }
}
