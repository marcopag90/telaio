package io.paganbit.telaio.metrics.collector;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.store.DalMetricsStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class DefaultDalMetricsFlushSchedulerTest {

    /**
     * A store that records the buckets it receives.
     */
    private static final class RecordingStore implements DalMetricsStore {
        final List<DalMetricsBucket> received = new ArrayList<>();

        @Override
        public void store(List<DalMetricsBucket> buckets) {
            received.addAll(buckets);
        }
    }

    private DalMetricsBucket sampleBucket() {
        return new DalMetricsBucket(
            Instant.parse("2026-06-12T10:00:00Z"), Duration.ofMinutes(1), "products",
            DalOperationType.READ, 1, 0, 1_000_000, 1_000_000, 1_000_000, new long[]{0, 1});
    }

    @Test
    void flushNow_shouldDrainCompletedAndFanOutToAllStores() {
        StubAggregator aggregator = new StubAggregator();
        aggregator.completed.add(sampleBucket());
        RecordingStore first = new RecordingStore();
        RecordingStore second = new RecordingStore();
        DalMetricsFlushScheduler scheduler = new DefaultDalMetricsFlushScheduler(
            aggregator, List.of(first, second), Duration.ofMinutes(1));

        scheduler.flushNow();

        assertThat(first.received).hasSize(1);
        assertThat(second.received).hasSize(1);
    }

    @Test
    void flushNow_withNothingToDrain_shouldNotCallStores() {
        StubAggregator aggregator = new StubAggregator();
        RecordingStore store = new RecordingStore();
        DalMetricsFlushScheduler scheduler = new DefaultDalMetricsFlushScheduler(
            aggregator, List.of(store), Duration.ofMinutes(1));

        scheduler.flushNow();

        assertThat(store.received).isEmpty();
    }

    @Test
    void stop_shouldDrainAllIncludingPartialBucket() {
        StubAggregator aggregator = new StubAggregator();
        aggregator.all.add(sampleBucket());
        RecordingStore store = new RecordingStore();
        DalMetricsFlushScheduler scheduler = new DefaultDalMetricsFlushScheduler(
            aggregator, List.of(store), Duration.ofMinutes(1));

        scheduler.stop();

        assertThat(store.received).hasSize(1);
        assertThat(scheduler.isRunning()).isFalse();
    }

    @Test
    void storeFailure_shouldBeIsolatedFromOtherStores() {
        StubAggregator aggregator = new StubAggregator();
        aggregator.completed.add(sampleBucket());
        DalMetricsStore failing = mock(DalMetricsStore.class);
        doThrow(new IllegalStateException("store down")).when(failing).store(anyList());
        RecordingStore healthy = new RecordingStore();
        DalMetricsFlushScheduler scheduler = new DefaultDalMetricsFlushScheduler(
            aggregator, List.of(failing, healthy), Duration.ofMinutes(1));

        assertThatCode(scheduler::flushNow).doesNotThrowAnyException();
        assertThat(healthy.received).hasSize(1);
    }

    @Test
    void startThenStop_shouldToggleRunningState() {
        StubAggregator aggregator = new StubAggregator();
        DalMetricsFlushScheduler scheduler = new DefaultDalMetricsFlushScheduler(
            aggregator, List.of(new RecordingStore()), Duration.ofMinutes(1));

        scheduler.start();
        assertThat(scheduler.isRunning()).isTrue();

        scheduler.stop();
        assertThat(scheduler.isRunning()).isFalse();
    }

    /**
     * An aggregator whose drain results are pre-seeded, avoiding any timing dependence.
     */
    private static final class StubAggregator implements DalMetricsAggregator {
        final List<DalMetricsBucket> completed = new ArrayList<>();
        final List<DalMetricsBucket> all = new ArrayList<>();

        @Override
        public void doRecord(String dalName, DalOperationType operation, long durationNanos, boolean error) {
            // not exercised by these tests
        }

        @Override
        public List<DalMetricsBucket> drainCompleted() {
            List<DalMetricsBucket> drained = new ArrayList<>(completed);
            completed.clear();
            return drained;
        }

        @Override
        public List<DalMetricsBucket> drainAll() {
            List<DalMetricsBucket> drained = new ArrayList<>(all);
            all.clear();
            return drained;
        }
    }
}
