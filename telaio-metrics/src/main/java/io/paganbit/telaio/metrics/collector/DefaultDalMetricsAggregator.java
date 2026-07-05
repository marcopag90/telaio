package io.paganbit.telaio.metrics.collector;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.model.LatencyHistogramScale;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Default {@link DalMetricsAggregator}: a lock-free, in-memory accumulator over fixed time windows.
 *
 * <p>Cells are keyed by {@code (bucketEpoch, dalName, operation)} where the epoch is the wall
 * clock divided by the bucket duration. Keying by epoch — rather than swapping structures on
 * window rotation — makes rotation race-free: a recorder that computed an older epoch simply
 * writes to that epoch's cell, and the next drain picks it up. Stores merge re-stored keys
 * additively, so nothing is lost.</p>
 *
 * <p>Recording is lock-free ({@link LongAdder}, CAS min/max, atomic histogram slots) and costs
 * well under a microsecond — negligible against any persistence-backed DAL operation.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultDalMetricsAggregator implements DalMetricsAggregator {

    private final ConcurrentHashMap<CellKey, LiveCell> cells = new ConcurrentHashMap<>();
    private final LatencyHistogramScale scale;
    private final Duration bucketDuration;
    private final Clock clock;

    public DefaultDalMetricsAggregator(LatencyHistogramScale scale, Duration bucketDuration, Clock clock) {
        if (bucketDuration.isNegative() || bucketDuration.isZero()) {
            throw new IllegalArgumentException("bucketDuration must be positive");
        }
        this.scale = scale;
        this.bucketDuration = bucketDuration;
        this.clock = clock;
    }

    @Override
    public void doRecord(String dalName, DalOperationType operation, long durationNanos, DalMetricsOutcome outcome) {
        CellKey key = new CellKey(currentEpoch(), dalName, operation);
        LiveCell cell = cells.computeIfAbsent(key, k -> new LiveCell(scale.bucketCount()));
        cell.count.increment();
        if (outcome == DalMetricsOutcome.ERROR) {
            cell.errorCount.increment();
        } else if (outcome == DalMetricsOutcome.CLIENT_ERROR) {
            cell.clientErrorCount.increment();
        }
        cell.sumNanos.add(durationNanos);
        cell.minNanos.accumulateAndGet(durationNanos, Math::min);
        cell.maxNanos.accumulateAndGet(durationNanos, Math::max);
        cell.histogram.incrementAndGet(scale.indexOf(durationNanos));
    }

    @Override
    public List<DalMetricsBucket> drainCompleted() {
        return drain(currentEpoch());
    }

    @Override
    public List<DalMetricsBucket> drainAll() {
        return drain(Long.MAX_VALUE);
    }

    private List<DalMetricsBucket> drain(long upToEpochExclusive) {
        List<DalMetricsBucket> drained = new ArrayList<>();
        for (CellKey key : cells.keySet()) {
            if (key.bucketEpoch() < upToEpochExclusive) {
                LiveCell cell = cells.remove(key);
                if (cell != null) {
                    drained.add(toBucket(key, cell));
                }
            }
        }
        return drained;
    }

    private long currentEpoch() {
        return clock.millis() / bucketDuration.toMillis();
    }

    private DalMetricsBucket toBucket(CellKey key, LiveCell cell) {
        long[] histogram = new long[scale.bucketCount()];
        for (int i = 0; i < histogram.length; i++) {
            histogram[i] = cell.histogram.get(i);
        }
        return new DalMetricsBucket(
            Instant.ofEpochMilli(key.bucketEpoch() * bucketDuration.toMillis()),
            bucketDuration,
            key.dalName(),
            key.operation(),
            cell.count.sum(),
            cell.errorCount.sum(),
            cell.clientErrorCount.sum(),
            cell.sumNanos.sum(),
            cell.minNanos.get(),
            cell.maxNanos.get(),
            histogram
        );
    }

    private record CellKey(long bucketEpoch, String dalName, DalOperationType operation) {
    }

    private static final class LiveCell {

        final LongAdder count = new LongAdder();
        final LongAdder errorCount = new LongAdder();
        final LongAdder clientErrorCount = new LongAdder();
        final LongAdder sumNanos = new LongAdder();
        final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxNanos = new AtomicLong(Long.MIN_VALUE);
        final AtomicLongArray histogram;

        LiveCell(int histogramBuckets) {
            this.histogram = new AtomicLongArray(histogramBuckets);
        }
    }
}
