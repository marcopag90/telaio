package com.paganbit.telaio.metrics.collector;

import com.paganbit.telaio.metrics.model.DalMetricsBucket;
import com.paganbit.telaio.metrics.store.DalMetricsStore;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link DalMetricsFlushScheduler}: periodically drains completed buckets from the
 * {@link DalMetricsAggregator} and fans them out to every registered {@link DalMetricsStore}.
 *
 * <p>Owns a dedicated single-thread daemon executor rather than relying on Spring's
 * {@code TaskScheduler}, which only exists when the host application enables scheduling — a
 * library must not impose that. On {@link #stop()} the executor is shut down and everything,
 * including the partial current bucket, is flushed so in-flight measurements survive shutdown.
 * A failing store is logged and isolated; it never affects other stores.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultDalMetricsFlushScheduler implements DalMetricsFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(DefaultDalMetricsFlushScheduler.class);

    private final DalMetricsAggregator aggregator;
    private final List<DalMetricsStore> stores;
    private final Duration flushInterval;

    private final AtomicReference<@Nullable ScheduledExecutorService> executor = new AtomicReference<>(null);

    public DefaultDalMetricsFlushScheduler(
        DalMetricsAggregator aggregator,
        List<DalMetricsStore> stores,
        Duration flushInterval
    ) {
        if (flushInterval.isNegative() || flushInterval.isZero()) {
            throw new IllegalArgumentException("flushInterval must be positive");
        }
        this.aggregator = aggregator;
        this.stores = List.copyOf(stores);
        this.flushInterval = flushInterval;
    }

    @Override
    public void start() {
        final var scheduledExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "telaio-metrics-flusher");
            thread.setDaemon(true);
            return thread;
        });
        scheduledExecutor.scheduleAtFixedRate(
            this::flushNow,
            flushInterval.toMillis(),
            flushInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
        this.executor.set(scheduledExecutor);
    }

    @Override
    public void stop() {
        final var scheduledExecutor = this.executor.getAndSet(null);
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdownNow();
        }
        fanOut(aggregator.drainAll());
    }

    @Override
    public boolean isRunning() {
        return executor.get() != null;
    }

    @Override
    public void flushNow() {
        try {
            fanOut(aggregator.drainCompleted());
        } catch (Exception e) {
            log.error("Failed to drain DAL metrics buckets", e);
        }
    }

    private void fanOut(List<DalMetricsBucket> buckets) {
        if (buckets.isEmpty()) {
            return;
        }
        for (DalMetricsStore store : stores) {
            try {
                store.store(buckets);
            } catch (Exception e) {
                log.error("DAL metrics store [{}] failed to persist {} buckets",
                    store.getClass().getSimpleName(), buckets.size(), e);
            }
        }
    }
}
