package com.paganbit.telaio.metrics.store;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.metrics.model.DalMetricsBucket;
import com.paganbit.telaio.metrics.model.DalMetricsStats;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;

/**
 * Merges {@link DalMetricsBucket}s into {@link DalMetricsStats}.
 *
 * <p>The single merge contract shared by every store implementation: counts and sums add, min/max
 * combine, and latency histograms add element-wise. Replace the bean to change how statistics —
 * notably percentiles — are derived from the raw buckets.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalMetricsBucketMerger {

    /**
     * Merges the given buckets into a single statistics view.
     *
     * @param dalName   registration name of the DAL the buckets belong to
     * @param operation the operation the buckets belong to, or {@code null} when merging across
     *                  operations
     * @param from      start of the queried range (inclusive)
     * @param to        end of the queried range (exclusive)
     * @param buckets   the buckets covering the range; may be empty
     * @return the merged statistics; all zero when {@code buckets} is empty
     */
    DalMetricsStats merge(
        String dalName,
        @Nullable DalOperationType operation,
        Instant from,
        Instant to,
        Collection<DalMetricsBucket> buckets
    );
}
