package com.paganbit.telaio.metrics.collector;

import com.paganbit.telaio.metrics.store.DalMetricsStore;
import org.springframework.context.SmartLifecycle;

/**
 * Drives the periodic draining of the {@link DalMetricsAggregator} into the registered
 * {@link DalMetricsStore}s.
 *
 * <p>It is a {@link SmartLifecycle}: started with the application context and, on shutdown,
 * expected to flush everything (including the partial current bucket) so in-flight measurements
 * survive. Replace the bean to change the scheduling strategy.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalMetricsFlushScheduler extends SmartLifecycle {

    /**
     * Drains completed buckets and stores them immediately. Invoked by the scheduler; public so
     * tests and callers needing fresh data can force a flush.
     */
    void flushNow();
}
