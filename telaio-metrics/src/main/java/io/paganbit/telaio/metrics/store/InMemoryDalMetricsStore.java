package io.paganbit.telaio.metrics.store;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.model.DalMetricsStats;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Bounded in-memory metrics store, used as the zero-configuration default when no persistent
 * store is available.
 *
 * <p>Buckets live in a {@link ConcurrentSkipListMap} ordered by {@code (bucketStart, dalName,
 * operation)}; collisions on the same key are merged additively. On every write, buckets older
 * than the configured retention are evicted, and the oldest are dropped once the bucket count
 * exceeds the configured cap — so the footprint is bounded regardless of traffic.</p>
 *
 * <p>Intended for demos and small single-instance deployments. Data does not survive a restart;
 * use the JDBC store for anything that must persist.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class InMemoryDalMetricsStore implements DalMetricsStore, DalMetricsQueryService {

    private final ConcurrentSkipListMap<BucketKey, DalMetricsBucket> buckets = new ConcurrentSkipListMap<>();
    private final DalMetricsBucketMerger merger;
    private final Duration retention;
    private final int maxBuckets;
    private final Clock clock;

    public InMemoryDalMetricsStore(
        DalMetricsBucketMerger merger,
        Duration retention,
        int maxBuckets,
        Clock clock
    ) {
        this.merger = merger;
        this.retention = retention;
        this.maxBuckets = maxBuckets;
        this.clock = clock;
    }

    @Override
    public void store(List<DalMetricsBucket> incoming) {
        for (DalMetricsBucket bucket : incoming) {
            buckets.merge(BucketKey.of(bucket), bucket, DalMetricsBucket::add);
        }
        evict();
    }

    private void evict() {
        Instant cutoff = clock.instant().minus(retention);
        buckets.entrySet().removeIf(entry -> entry.getKey().bucketStart().isBefore(cutoff));
        while (buckets.size() > maxBuckets) {
            Map.Entry<BucketKey, DalMetricsBucket> oldest = buckets.pollFirstEntry();
            if (oldest == null) {
                break;
            }
        }
    }

    @Override
    public List<String> dalNames(Instant from, Instant to) {
        TreeSet<String> names = new TreeSet<>();
        for (DalMetricsBucket bucket : range(from, to)) {
            names.add(bucket.dalName());
        }
        return List.copyOf(names);
    }

    @Override
    public DalMetricsStats stats(String dalName, @Nullable DalOperationType operation, Instant from, Instant to) {
        List<DalMetricsBucket> matching = new ArrayList<>();
        for (DalMetricsBucket bucket : range(from, to)) {
            if (bucket.dalName().equals(dalName) && (operation == null || bucket.operation() == operation)) {
                matching.add(bucket);
            }
        }
        return merger.merge(dalName, operation, from, to, matching);
    }

    @Override
    public Map<DalOperationType, DalMetricsStats> statsByOperation(String dalName, Instant from, Instant to) {
        Map<DalOperationType, List<DalMetricsBucket>> byOperation = new EnumMap<>(DalOperationType.class);
        for (DalMetricsBucket bucket : range(from, to)) {
            if (bucket.dalName().equals(dalName)) {
                byOperation.computeIfAbsent(bucket.operation(), k -> new ArrayList<>()).add(bucket);
            }
        }
        Map<DalOperationType, DalMetricsStats> result = new EnumMap<>(DalOperationType.class);
        byOperation.forEach((operation, operationBuckets) ->
            result.put(operation, merger.merge(dalName, operation, from, to, operationBuckets)));
        return result;
    }

    @Override
    public List<DalMetricsStats> topSlowest(int limit, Instant from, Instant to) {
        record DalAndOperation(String dalName, DalOperationType operation) {
        }
        Map<DalAndOperation, List<DalMetricsBucket>> grouped = new java.util.HashMap<>();
        for (DalMetricsBucket bucket : range(from, to)) {
            grouped.computeIfAbsent(new DalAndOperation(bucket.dalName(), bucket.operation()),
                k -> new ArrayList<>()).add(bucket);
        }
        return grouped.entrySet().stream()
            .map(entry -> merger.merge(
                entry.getKey().dalName(), entry.getKey().operation(), from, to, entry.getValue()))
            .filter(stats -> stats.count() > 0)
            .sorted(Comparator.comparing(DalMetricsStats::mean).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public List<DalMetricsBucket> findBuckets(
        @Nullable String dalName,
        @Nullable DalOperationType operation,
        Instant from,
        Instant to
    ) {
        List<DalMetricsBucket> result = new ArrayList<>();
        for (DalMetricsBucket bucket : range(from, to)) {
            if ((dalName == null || bucket.dalName().equals(dalName))
                && (operation == null || bucket.operation() == operation)) {
                result.add(bucket);
            }
        }
        return result;
    }

    private Iterable<DalMetricsBucket> range(Instant from, Instant to) {
        return buckets.subMap(BucketKey.lowerBound(from), true, BucketKey.lowerBound(to), false)
            .values();
    }

    /**
     * Composite ordering key. {@link #lowerBound(Instant)} produces a sentinel for range scans
     * that sorts before any real key at the same instant.
     */
    private record BucketKey(Instant bucketStart, String dalName, String operation)
        implements Comparable<BucketKey> {

        static BucketKey of(DalMetricsBucket bucket) {
            return new BucketKey(bucket.bucketStart(), bucket.dalName(), bucket.operation().name());
        }

        static BucketKey lowerBound(Instant instant) {
            return new BucketKey(instant, "", "");
        }

        @Override
        public int compareTo(BucketKey other) {
            int byInstant = bucketStart.compareTo(other.bucketStart);
            if (byInstant != 0) {
                return byInstant;
            }
            int byDal = dalName.compareTo(other.dalName);
            if (byDal != 0) {
                return byDal;
            }
            return operation.compareTo(other.operation);
        }
    }
}
