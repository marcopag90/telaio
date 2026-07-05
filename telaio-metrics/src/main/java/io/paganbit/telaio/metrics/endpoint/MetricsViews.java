package io.paganbit.telaio.metrics.endpoint;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.model.DalMetricsStats;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Response DTOs for the {@code telaiometrics} actuator endpoint, plus the mapping from internal
 * {@link DalMetricsStats} to the millisecond-based views exposed over HTTP/JMX.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public final class MetricsViews {

    private MetricsViews() {
    }

    /**
     * Usage and latency figures, with durations expressed in milliseconds for readability.
     */
    public record StatsView(
        long count,
        long errorCount,
        long clientErrorCount,
        double meanMs,
        double minMs,
        double maxMs,
        double totalMs,
        Map<String, Double> percentilesMs
    ) {
        static StatsView of(DalMetricsStats stats) {
            Map<String, Double> percentiles = new LinkedHashMap<>();
            stats.percentiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> percentiles.put(quantileKey(e.getKey()), toMillis(e.getValue())));
            return new StatsView(
                stats.count(),
                stats.errorCount(),
                stats.clientErrorCount(),
                toMillis(stats.mean()),
                toMillis(stats.min()),
                toMillis(stats.max()),
                toMillis(stats.totalDuration()),
                percentiles);
        }
    }

    /**
     * One row in the overview: a DAL with its merged-across-operations figures.
     */
    public record DalSummaryView(String dalName, StatsView stats) {
    }

    /**
     * One row in the slowest ranking: a (DAL, operation) pair with its figures.
     */
    public record OperationRankView(String dalName, DalOperationType operation, StatsView stats) {
    }

    /**
     * The top-level overview returned by {@code GET /actuator/telaiometrics}.
     */
    public record OverviewView(
        Instant from,
        Instant to,
        List<DalSummaryView> dals,
        List<OperationRankView> slowest
    ) {
    }

    /**
     * A single DAL drill-down returned by {@code GET /actuator/telaiometrics/{dalName}}.
     */
    public record DalView(
        String dalName,
        Instant from,
        Instant to,
        StatsView overall,
        Map<DalOperationType, StatsView> operations
    ) {
    }

    /**
     * A single operation drill-down returned by
     * {@code GET /actuator/telaiometrics/{dalName}/{operation}}.
     */
    public record OperationView(
        String dalName,
        DalOperationType operation,
        Instant from,
        Instant to,
        StatsView stats
    ) {
    }

    static String quantileKey(double quantile) {
        return "p" + (int) Math.round(quantile * 100);
    }

    static double toMillis(@Nullable Duration duration) {
        return duration == null ? 0.0 : duration.toNanos() / 1_000_000.0;
    }
}
