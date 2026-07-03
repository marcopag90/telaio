package io.paganbit.telaio.metrics.model;

import io.paganbit.telaio.core.adapter.DalOperationType;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Aggregated statistics for one (DAL, operation) pair over one collection window.
 *
 * <p>Buckets are the unit of persistence: the in-memory aggregator produces them when a window
 * completes and stores persist them. All fields are mergeable — counts and sums add, min/max
 * combine, and {@code histogramCounts} adds element-wise when recorded against the same
 * {@link LatencyHistogramScale} — so statistics over any time range are computed by merging the
 * buckets it covers.</p>
 *
 * <p>Outcomes are SUCCESS or ERROR only ({@code count - errorCount} = successes): authorization
 * denials happen at the web operation-adapter boundary and never reach the {@code Dal} bean, so
 * they are not DAL usage and are not observable here.</p>
 *
 * @param bucketStart        start of the collection window (inclusive)
 * @param bucketDuration     length of the collection window
 * @param dalName            registration name of the DAL
 * @param operation          the DAL operation
 * @param count              total invocations
 * @param errorCount         invocations that threw
 * @param totalDurationNanos sum of elapsed times, in nanoseconds
 * @param minDurationNanos   smallest elapsed time, in nanoseconds
 * @param maxDurationNanos   largest elapsed time, in nanoseconds
 * @param histogramCounts    latency histogram counts; length equals the scale's bucket count,
 *                           the last element is the overflow bucket
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalMetricsBucket(
    Instant bucketStart,
    Duration bucketDuration,
    String dalName,
    DalOperationType operation,
    long count,
    long errorCount,
    long totalDurationNanos,
    long minDurationNanos,
    long maxDurationNanos,
    long[] histogramCounts
) {

    /**
     * Combines this bucket with another bearing the same key, adding it. Used by stores when the
     * same {@code (bucketStart, dalName, operation)} is persisted more than once — a late-recorded
     * cell drained on a later flush, or the partial bucket flushed at shutdown.
     *
     * <p>Histograms are added element-wise when their lengths match; on a mismatch (a scale change
     * between the two recordings) this bucket's histogram is kept, since the counts are no longer
     * comparable.</p>
     *
     * @param other the bucket to add; must share this bucket's key
     * @return the combined bucket
     */
    public DalMetricsBucket add(DalMetricsBucket other) {
        long[] mergedHistogram;
        if (histogramCounts.length == other.histogramCounts.length) {
            mergedHistogram = histogramCounts.clone();
            for (int i = 0; i < mergedHistogram.length; i++) {
                mergedHistogram[i] += other.histogramCounts[i];
            }
        } else {
            mergedHistogram = histogramCounts.clone();
        }
        return new DalMetricsBucket(
            bucketStart,
            bucketDuration,
            dalName,
            operation,
            count + other.count,
            errorCount + other.errorCount,
            totalDurationNanos + other.totalDurationNanos,
            Math.min(minDurationNanos, other.minDurationNanos),
            Math.max(maxDurationNanos, other.maxDurationNanos),
            mergedHistogram
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DalMetricsBucket(
            Instant start, Duration duration, String name,
            DalOperationType operation1, long count1, long errorCount1,
            long durationNanos, long nanos, long maxDurationNanos1, long[] counts
        ))) return false;
        return count == count1
            && errorCount == errorCount1
            && totalDurationNanos == durationNanos
            && minDurationNanos == nanos
            && maxDurationNanos == maxDurationNanos1
            && Objects.equals(bucketStart, start)
            && Objects.equals(bucketDuration, duration)
            && Objects.equals(dalName, name)
            && operation == operation1
            && Arrays.equals(histogramCounts, counts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            bucketStart, bucketDuration, dalName, operation,
            count, errorCount, totalDurationNanos, minDurationNanos, maxDurationNanos,
            Arrays.hashCode(histogramCounts)
        );
    }

    @Override
    public @NonNull String toString() {
        return "DalMetricsBucket[" +
            "bucketStart=" + bucketStart + ", " +
            "bucketDuration=" + bucketDuration + ", " +
            "dalName='" + dalName + "', " +
            "operation=" + operation + ", " +
            "count=" + count + ", " +
            "errorCount=" + errorCount + ", " +
            "totalDurationNanos=" + totalDurationNanos + ", " +
            "minDurationNanos=" + minDurationNanos + ", " +
            "maxDurationNanos=" + maxDurationNanos + ", " +
            "histogramCounts=" + Arrays.toString(histogramCounts) +
            ']';
    }
}
