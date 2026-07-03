package io.paganbit.telaio.metrics.collector;

import io.paganbit.telaio.core.adapter.DalOperationType;

/**
 * Sink for individual DAL invocation measurements.
 *
 * <p>The {@link DalMetricsInterceptor} times every measured invocation and hands it to all
 * registered recorders. This is the seam that lets a measurement be processed in more than one
 * way: the in-house {@link DalMetricsAggregator} buckets it for the {@code telaiometrics} endpoint,
 * while {@code MicrometerDalMetricsRecorder} feeds it to a Micrometer {@code MeterRegistry} for scraping.
 * Implementations must be thread-safe — recording happens on arbitrary caller threads.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalMetricsRecorder {

    /**
     * Records one DAL invocation.
     *
     * @param dalName       registration name of the DAL
     * @param operation     the invoked operation
     * @param durationNanos elapsed time in nanoseconds
     * @param error         whether the invocation threw
     */
    void doRecord(String dalName, DalOperationType operation, long durationNanos, boolean error);
}
