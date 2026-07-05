package io.paganbit.telaio.metrics.collector;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.model.LatencyHistogramScale;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDalMetricsAggregatorTest {

    private final LatencyHistogramScale scale = LatencyHistogramScale.of(Duration.ofMillis(1), 2.0, 4);

    /**
     * A clock whose instant can be advanced between calls, to drive bucket rotation deterministically.
     */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public long millis() {
            return instant.toEpochMilli();
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void doRecord_shouldAccumulateScalarsAndHistogram() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T10:00:00Z"));
        DalMetricsAggregator aggregator = new DefaultDalMetricsAggregator(scale, Duration.ofMinutes(1), clock);

        // Two client errors vs one error: distinct values, so a counter swap cannot pass unnoticed.
        aggregator.doRecord("products", DalOperationType.READ, 1_500_000, DalMetricsOutcome.CLIENT_ERROR); // bucket 1
        aggregator.doRecord("products", DalOperationType.READ, 500_000, DalMetricsOutcome.CLIENT_ERROR);   // bucket 0
        aggregator.doRecord("products", DalOperationType.READ, 3_000_000, DalMetricsOutcome.ERROR);        // bucket 2

        clock.advance(Duration.ofMinutes(1));
        List<DalMetricsBucket> buckets = aggregator.drainCompleted();

        assertThat(buckets).hasSize(1);
        DalMetricsBucket bucket = buckets.getFirst();
        assertThat(bucket.dalName()).isEqualTo("products");
        assertThat(bucket.operation()).isEqualTo(DalOperationType.READ);
        assertThat(bucket.count()).isEqualTo(3);
        assertThat(bucket.errorCount()).isEqualTo(1);
        assertThat(bucket.clientErrorCount()).isEqualTo(2);
        assertThat(bucket.totalDurationNanos()).isEqualTo(5_000_000);
        assertThat(bucket.minDurationNanos()).isEqualTo(500_000);
        assertThat(bucket.maxDurationNanos()).isEqualTo(3_000_000);
        assertThat(bucket.histogramCounts()).containsExactly(1, 1, 1, 0);
    }

    @Test
    void drainCompleted_shouldLeaveTheCurrentWindowInPlace() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T10:00:00Z"));
        DalMetricsAggregator aggregator = new DefaultDalMetricsAggregator(scale, Duration.ofMinutes(1), clock);

        aggregator.doRecord("products", DalOperationType.READ, 1_000_000, DalMetricsOutcome.SUCCESS);

        // Same window: nothing completed yet
        assertThat(aggregator.drainCompleted()).isEmpty();

        clock.advance(Duration.ofMinutes(1));
        assertThat(aggregator.drainCompleted()).hasSize(1);
        // Drained once, gone afterward
        assertThat(aggregator.drainCompleted()).isEmpty();
    }

    @Test
    void recordingAfterRotation_shouldProduceSeparateBuckets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T10:00:00Z"));
        DalMetricsAggregator aggregator = new DefaultDalMetricsAggregator(scale, Duration.ofMinutes(1), clock);

        aggregator.doRecord("products", DalOperationType.READ, 1_000_000, DalMetricsOutcome.SUCCESS);
        clock.advance(Duration.ofMinutes(1));
        aggregator.doRecord("products", DalOperationType.READ, 1_000_000, DalMetricsOutcome.SUCCESS);

        // First window completed; second window still current
        assertThat(aggregator.drainCompleted()).hasSize(1);
        clock.advance(Duration.ofMinutes(1));
        assertThat(aggregator.drainCompleted()).hasSize(1);
    }

    @Test
    void drainAll_shouldIncludeThePartialCurrentWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T10:00:00Z"));
        DalMetricsAggregator aggregator = new DefaultDalMetricsAggregator(scale, Duration.ofMinutes(1), clock);

        aggregator.doRecord("products", DalOperationType.READ, 1_000_000, DalMetricsOutcome.SUCCESS);

        // No rotation, but drainAll takes the partial bucket anyway
        assertThat(aggregator.drainAll()).hasSize(1);
        assertThat(aggregator.drainAll()).isEmpty();
    }

    @Test
    void doRecord_shouldSeparateDalsAndOperations() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T10:00:00Z"));
        DalMetricsAggregator aggregator = new DefaultDalMetricsAggregator(scale, Duration.ofMinutes(1), clock);

        aggregator.doRecord("products", DalOperationType.READ, 1_000_000, DalMetricsOutcome.SUCCESS);
        aggregator.doRecord("products", DalOperationType.CREATE, 1_000_000, DalMetricsOutcome.SUCCESS);
        aggregator.doRecord("orders", DalOperationType.READ, 1_000_000, DalMetricsOutcome.SUCCESS);

        assertThat(aggregator.drainAll()).hasSize(3);
    }

    @Test
    void doRecord_shouldBeThreadSafe() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T10:00:00Z"));
        DalMetricsAggregator aggregator = new DefaultDalMetricsAggregator(scale, Duration.ofMinutes(1), clock);

        int threads = 8;
        int perThread = 10_000;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    await(start);
                    for (int i = 0; i < perThread; i++) {
                        aggregator.doRecord("products", DalOperationType.READ, 1_500_000, DalMetricsOutcome.SUCCESS);
                    }
                });
            }
            start.countDown();
        }
        // try-with-resources close() awaits termination of every submitted task

        List<DalMetricsBucket> buckets = aggregator.drainAll();
        assertThat(buckets).hasSize(1);
        DalMetricsBucket bucket = buckets.getFirst();
        assertThat(bucket.count()).isEqualTo((long) threads * perThread);
        assertThat(bucket.histogramCounts()[1]).isEqualTo((long) threads * perThread);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
