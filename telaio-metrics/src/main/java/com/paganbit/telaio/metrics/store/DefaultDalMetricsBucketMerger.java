package com.paganbit.telaio.metrics.store;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.metrics.model.DalMetricsBucket;
import com.paganbit.telaio.metrics.model.DalMetricsStats;
import com.paganbit.telaio.metrics.model.LatencyHistogramScale;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link DalMetricsBucketMerger}: counts and sums add, min/max combine, and histograms add
 * element-wise. Buckets whose histogram length does not match the configured
 * {@link LatencyHistogramScale} — typically persisted before a scale change — still contribute
 * their scalar statistics but are skipped for percentile purposes.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultDalMetricsBucketMerger implements DalMetricsBucketMerger {

    private final LatencyHistogramScale scale;
    private final List<Double> quantiles;

    public DefaultDalMetricsBucketMerger(LatencyHistogramScale scale, List<Double> quantiles) {
        this.scale = scale;
        this.quantiles = List.copyOf(quantiles);
    }

    @Override
    public DalMetricsStats merge(
        String dalName,
        @Nullable DalOperationType operation,
        Instant from,
        Instant to,
        Collection<DalMetricsBucket> buckets
    ) {
        long count = 0;
        long errorCount = 0;
        long clientErrorCount = 0;
        long totalNanos = 0;
        long minNanos = Long.MAX_VALUE;
        long maxNanos = Long.MIN_VALUE;
        long[] histogram = new long[scale.bucketCount()];

        for (DalMetricsBucket bucket : buckets) {
            if (bucket.count() == 0) {
                continue;
            }
            count += bucket.count();
            errorCount += bucket.errorCount();
            clientErrorCount += bucket.clientErrorCount();
            totalNanos += bucket.totalDurationNanos();
            minNanos = Math.min(minNanos, bucket.minDurationNanos());
            maxNanos = Math.max(maxNanos, bucket.maxDurationNanos());
            long[] counts = bucket.histogramCounts();
            if (counts.length == histogram.length) {
                for (int i = 0; i < histogram.length; i++) {
                    histogram[i] += counts[i];
                }
            }
        }

        if (count == 0) {
            return new DalMetricsStats(dalName, operation, from, to, 0, 0, 0,
                Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Map.of());
        }

        Map<Double, Duration> percentiles = new LinkedHashMap<>();
        for (double quantile : quantiles) {
            percentiles.put(quantile, scale.percentile(histogram, quantile));
        }
        return new DalMetricsStats(
            dalName,
            operation,
            from,
            to,
            count,
            errorCount,
            clientErrorCount,
            Duration.ofNanos(totalNanos),
            Duration.ofNanos(minNanos),
            Duration.ofNanos(maxNanos),
            Duration.ofNanos(totalNanos / count),
            Map.copyOf(percentiles)
        );
    }
}
