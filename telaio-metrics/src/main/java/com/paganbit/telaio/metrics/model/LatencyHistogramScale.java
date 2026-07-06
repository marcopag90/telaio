package com.paganbit.telaio.metrics.model;

import java.time.Duration;
import java.util.Arrays;

/**
 * Fixed geometric bucket boundaries for latency histograms.
 *
 * <p>The scale defines {@code bucketCount} buckets: the first {@code bucketCount - 1} have finite
 * upper bounds growing geometrically from {@code firstUpperBound} by {@code growthFactor}; the
 * last bucket is the overflow and has no upper bound. Histogram counts recorded against the same
 * scale are mergeable by element-wise addition, which is what makes percentiles computable at
 * query time across persisted buckets.</p>
 *
 * <p>Changing the scale settings makes previously persisted histograms incomparable; the merger
 * skips them for percentile purposes (their scalar statistics still contribute).</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public final class LatencyHistogramScale {

    private final long[] upperBoundsNanos;
    private final int bucketCount;

    private LatencyHistogramScale(long[] upperBoundsNanos, int bucketCount) {
        this.upperBoundsNanos = upperBoundsNanos;
        this.bucketCount = bucketCount;
    }

    /**
     * Creates a scale of {@code bucketCount} buckets whose finite upper bounds start at
     * {@code firstUpperBound} and grow by {@code growthFactor}.
     *
     * @param firstUpperBound the upper bound of the first bucket must be positive
     * @param growthFactor    geometric growth factor must be greater than 1
     * @param bucketCount     total number of buckets including the overflow bucket, at least 2
     * @return the scale
     */
    public static LatencyHistogramScale of(Duration firstUpperBound, double growthFactor, int bucketCount) {
        if (firstUpperBound.isNegative() || firstUpperBound.isZero()) {
            throw new IllegalArgumentException("firstUpperBound must be positive");
        }
        if (growthFactor <= 1.0) {
            throw new IllegalArgumentException("growthFactor must be greater than 1");
        }
        if (bucketCount < 2) {
            throw new IllegalArgumentException("bucketCount must be at least 2");
        }
        long[] bounds = new long[bucketCount - 1];
        double bound = firstUpperBound.toNanos();
        for (int i = 0; i < bounds.length; i++) {
            bounds[i] = Math.clamp((long) bound, 0, Long.MAX_VALUE);
            bound *= growthFactor;
        }
        return new LatencyHistogramScale(bounds, bucketCount);
    }

    /**
     * Total number of buckets, including the unbounded overflow bucket.
     */
    public int bucketCount() {
        return bucketCount;
    }

    /**
     * Finite upper bounds in nanoseconds, ascending; the overflow bucket has no entry.
     */
    public long[] upperBoundsNanos() {
        return upperBoundsNanos.clone();
    }

    /**
     * Returns the index of the bucket that contains the given duration.
     *
     * @param durationNanos elapsed time in nanoseconds
     * @return bucket index in {@code [0, bucketCount - 1]}
     */
    public int indexOf(long durationNanos) {
        int idx = Arrays.binarySearch(upperBoundsNanos, durationNanos);
        return idx >= 0 ? idx : Math.clamp((int) (-(long) idx - 1), 0, bucketCount - 1);
    }

    /**
     * Computes an approximate percentile from histogram counts recorded against this scale,
     * using linear interpolation within the containing bucket. Values falling in the overflow
     * bucket are clamped to the last finite bound.
     *
     * @param counts   histogram counts, length must be {@link #bucketCount()}
     * @param quantile the quantile in {@code (0, 1]}
     * @return the approximate latency at the given quantile, or {@link Duration#ZERO} when the
     * histogram is empty
     */
    public Duration percentile(long[] counts, double quantile) {
        validatePercentileInputs(counts, quantile);
        long total = computeTotal(counts);
        if (total == 0) {
            return Duration.ZERO;
        }
        return findBucketAndInterpolate(counts, quantile * total);
    }

    private void validatePercentileInputs(long[] counts, double quantile) {
        if (counts.length != bucketCount) {
            throw new IllegalArgumentException(
                "counts length " + counts.length + " does not match bucketCount " + bucketCount);
        }
        if (quantile <= 0.0 || quantile > 1.0) {
            throw new IllegalArgumentException("quantile must be in (0, 1]");
        }
    }

    private long computeTotal(long[] counts) {
        long total = 0;
        for (long count : counts) {
            total += count;
        }
        return total;
    }

    private Duration findBucketAndInterpolate(long[] counts, double targetRank) {
        long cumulative = 0;
        for (int i = 0; i < bucketCount; i++) {
            if (counts[i] == 0) {
                continue;
            }
            long previousCumulative = cumulative;
            cumulative += counts[i];
            if (cumulative >= targetRank) {
                if (i == bucketCount - 1) {
                    return Duration.ofNanos(upperBoundsNanos[upperBoundsNanos.length - 1]);
                }
                long lowerBound = i == 0 ? 0 : upperBoundsNanos[i - 1];
                long upperBound = upperBoundsNanos[i];
                double positionInBucket = (targetRank - previousCumulative) / counts[i];
                return Duration.ofNanos(lowerBound + (long) ((upperBound - lowerBound) * positionInBucket));
            }
        }
        return Duration.ofNanos(upperBoundsNanos[upperBoundsNanos.length - 1]);
    }
}
