package io.paganbit.telaio.metrics.collector;

import io.paganbit.telaio.metrics.model.DalMetricsBucket;

import java.util.List;

/**
 * Live accumulator of DAL invocation metrics over fixed time windows.
 *
 * <p>The interceptor records every measured invocation here; the flush scheduler periodically
 * drains completed windows and persists them. Implementations must be thread-safe: recording
 * happens on arbitrary caller threads while draining happens on the flusher thread.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalMetricsAggregator extends DalMetricsRecorder {

    /**
     * Removes and returns the buckets of every completed window. The current, still-accumulating
     * window is left in place.
     *
     * @return the completed buckets; empty when nothing completed since the last drain
     */
    List<DalMetricsBucket> drainCompleted();

    /**
     * Removes and returns every bucket, including the partial current window. Called on shutdown,
     * so in-flight measurements are not lost; partial buckets merge cleanly because stores add on
     * key collisions.
     *
     * @return all buckets; empty when nothing was recorded since the last drain
     */
    List<DalMetricsBucket> drainAll();
}
