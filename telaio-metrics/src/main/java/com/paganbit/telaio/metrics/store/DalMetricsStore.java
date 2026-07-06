package com.paganbit.telaio.metrics.store;

import com.paganbit.telaio.metrics.model.DalMetricsBucket;

import java.util.List;

/**
 * Write side of the metrics storage SPI: persists completed buckets.
 *
 * <p>The flush scheduler fans completed buckets out to <em>every</em> {@code DalMetricsStore}
 * bean, off the request path, so additional sinks (a monitoring bridge, another datastore) are
 * plugged in by simply registering more beans. A store failure is logged and isolated — it never
 * affects other stores or business operations.</p>
 *
 * <p>The same bucket key {@code (bucketStart, dalName, operation)} may be stored more than once
 * (a late-recorded cell drained on a later flush, or the partial bucket flushed at shutdown):
 * implementations must merge such collisions additively, e.g. via
 * {@link DalMetricsBucketMerger}-equivalent semantics.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalMetricsStore {

    /**
     * Persists the given completed buckets.
     *
     * @param buckets the buckets to persist; never empty
     */
    void store(List<DalMetricsBucket> buckets);
}
