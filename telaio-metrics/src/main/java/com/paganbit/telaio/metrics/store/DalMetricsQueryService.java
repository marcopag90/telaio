package com.paganbit.telaio.metrics.store;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.metrics.model.DalMetricsBucket;
import com.paganbit.telaio.metrics.model.DalMetricsStats;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read side of the metrics storage SPI: answers usage and latency queries over persisted buckets.
 *
 * <p>Consumed by the {@code telaiometrics} actuator endpoint and available for programmatic use.
 * Results reflect flushed buckets only, so very recent activity may lag by up to one bucket
 * duration plus one flush interval.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalMetricsQueryService {

    /**
     * Returns the names of the DALs with recorded activity in the given range.
     *
     * @param from start of the range (inclusive)
     * @param to   end of the range (exclusive)
     * @return distinct DAL names, sorted
     */
    List<String> dalNames(Instant from, Instant to);

    /**
     * Returns merged statistics for a DAL — or one of its operations — over the given range.
     *
     * @param dalName   registration name of the DAL
     * @param operation the operation, or {@code null} to merge across all operations
     * @param from      start of the range (inclusive)
     * @param to        end of the range (exclusive)
     * @return the statistics; all zero when no activity was recorded
     */
    DalMetricsStats stats(String dalName, @Nullable DalOperationType operation, Instant from, Instant to);

    /**
     * Returns per-operation statistics for a DAL over the given range. Operations with no
     * recorded activity are omitted.
     *
     * @param dalName registration name of the DAL
     * @param from    start of the range (inclusive)
     * @param to      end of the range (exclusive)
     * @return statistics keyed by operation
     */
    Map<DalOperationType, DalMetricsStats> statsByOperation(String dalName, Instant from, Instant to);

    /**
     * Returns the (DAL, operation) pairs with recorded activity in the given range, ranked by
     * mean duration descending — the primary tool for spotting slow methods.
     *
     * @param limit maximum number of entries to return
     * @param from  start of the range (inclusive)
     * @param to    end of the range (exclusive)
     * @return up to {@code limit} statistics entries, slowest first
     */
    List<DalMetricsStats> topSlowest(int limit, Instant from, Instant to);

    /**
     * Returns the raw buckets matching the given criteria, ordered by bucket start. Intended for
     * trend views and exports; most callers want the merged statistics methods instead.
     *
     * @param dalName   registration name of the DAL, or {@code null} for all DALs
     * @param operation the operation, or {@code null} for all operations
     * @param from      start of the range (inclusive)
     * @param to        end of the range (exclusive)
     * @return the matching buckets
     */
    List<DalMetricsBucket> findBuckets(
        @Nullable String dalName,
        @Nullable DalOperationType operation,
        Instant from,
        Instant to
    );
}
