package com.paganbit.telaio.metrics.model;

import com.paganbit.telaio.core.adapter.DalOperationType;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Usage and latency statistics for a DAL — or one of its operations — over a time range.
 *
 * <p>Produced by merging the {@link DalMetricsBucket}s the range covers. Percentiles are
 * approximate: they are interpolated from the merged latency histogram.</p>
 *
 * @param dalName       registration name of the DAL
 * @param operation     the DAL operation, or {@code null} when merged across all operations
 * @param from          start of the queried range (inclusive)
 * @param to            end of the queried range (exclusive)
 * @param count            total invocations
 * @param errorCount       invocations that failed because of the service
 * @param clientErrorCount invocations that failed because of the caller (validation, not-found,
 *                         conflict — see {@link com.paganbit.telaio.core.exception.DalFailureKind})
 * @param totalDuration sum of elapsed times
 * @param min           smallest elapsed time
 * @param max           largest elapsed time
 * @param mean          average elapsed time
 * @param percentiles   approximate latencies keyed by quantile (0.5, 0.9, 0.95, 0.99)
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalMetricsStats(
    String dalName,
    @Nullable DalOperationType operation,
    Instant from,
    Instant to,
    long count,
    long errorCount,
    long clientErrorCount,
    Duration totalDuration,
    Duration min,
    Duration max,
    Duration mean,
    Map<Double, Duration> percentiles
) {
}
