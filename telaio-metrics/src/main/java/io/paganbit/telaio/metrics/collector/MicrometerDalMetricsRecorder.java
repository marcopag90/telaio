package io.paganbit.telaio.metrics.collector;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.paganbit.telaio.core.adapter.DalOperationType;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Records DAL invocation timings into a Micrometer {@link MeterRegistry}.
 *
 * <p>Each invocation feeds a {@link Timer} named {@code metricName} (default
 * {@code telaio.dal.operation}) tagged with {@code dal}, {@code operation}, and {@code outcome}
 * ({@code success}/{@code error}). The application's configured Micrometer registry then exports
 * the timings to whatever monitoring backend it is bound to — Telaio stays agnostic of it.</p>
 *
 * <p>Percentiles are published as a histogram ({@link Timer.Builder#publishPercentileHistogram()})
 * rather than as pre-computed quantiles: histograms can be merged across application instances, so
 * percentiles stay accurate after aggregation, whereas per-instance quantiles cannot be combined.
 * This recorder is the Micrometer counterpart of the in-house {@link DalMetricsAggregator} and is
 * activated by {@code telaio.metrics.micrometer.enabled=true}.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class MicrometerDalMetricsRecorder implements DalMetricsRecorder {

    private final MeterRegistry registry;
    private final String metricName;

    public MicrometerDalMetricsRecorder(MeterRegistry registry, String metricName) {
        this.registry = registry;
        this.metricName = metricName;
    }

    @Override
    public void doRecord(String dalName, DalOperationType operation, long durationNanos, boolean error) {
        Timer.builder(metricName)
            .tag("dal", dalName)
            .tag("operation", operation.name().toLowerCase(Locale.ROOT))
            .tag("outcome", error ? "error" : "success")
            .publishPercentileHistogram()
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
