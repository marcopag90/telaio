package com.paganbit.telaio.metrics.endpoint;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties;
import com.paganbit.telaio.metrics.store.DalMetricsQueryService;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Actuator endpoint exposing DAL usage and performance statistics.
 *
 * <p>Reachable over any actuator transport (HTTP and JMX) at id {@code telaiometrics}:</p>
 * <ul>
 *   <li>{@code GET /actuator/telaiometrics} — per-DAL summary and the slowest operations;</li>
 *   <li>{@code GET /actuator/telaiometrics/{dalName}} — one DAL, overall and per operation;</li>
 *   <li>{@code GET /actuator/telaiometrics/{dalName}/{operation}} — one operation.</li>
 * </ul>
 *
 * <p>All read operations accept optional ISO-8601 {@code from}/{@code to} instants; when omitted
 * the range is the configured default ending at now. Durations in the response are milliseconds.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@Endpoint(id = "telaiometrics")
public class TelaioMetricsEndpoint {

    private final DalMetricsQueryService queryService;
    private final TelaioMetricsProperties.Endpoint properties;
    private final Clock clock;

    public TelaioMetricsEndpoint(
        DalMetricsQueryService queryService,
        TelaioMetricsProperties.Endpoint properties,
        Clock clock
    ) {
        this.queryService = queryService;
        this.properties = properties;
        this.clock = clock;
    }

    @ReadOperation
    public MetricsViews.OverviewView overview(
        @Nullable String from,
        @Nullable String to,
        @Nullable Integer top
    ) {
        Range range = resolveRange(from, to);
        int limit = top != null ? top : properties.getTopN();

        List<MetricsViews.DalSummaryView> dals = queryService.dalNames(range.from(), range.to()).stream()
            .map(name -> new MetricsViews.DalSummaryView(name,
                MetricsViews.StatsView.of(queryService.stats(name, null, range.from(), range.to()))))
            .toList();

        List<MetricsViews.OperationRankView> slowest =
            queryService.topSlowest(limit, range.from(), range.to()).stream()
                .map(stats -> new MetricsViews.OperationRankView(
                    stats.dalName(), stats.operation(), MetricsViews.StatsView.of(stats)))
                .toList();

        return new MetricsViews.OverviewView(range.from(), range.to(), dals, slowest);
    }

    @ReadOperation
    public MetricsViews.DalView dal(
        @Selector String dalName,
        @Nullable String from,
        @Nullable String to
    ) {
        Range range = resolveRange(from, to);
        Map<DalOperationType, MetricsViews.StatsView> operations =
            queryService
                .statsByOperation(dalName, range.from(), range.to()).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> MetricsViews.StatsView.of(entry.getValue())));
        MetricsViews.StatsView overall =
            MetricsViews.StatsView.of(queryService.stats(dalName, null, range.from(), range.to()));
        return new MetricsViews.DalView(dalName, range.from(), range.to(), overall, operations);
    }

    @ReadOperation
    public MetricsViews.OperationView operation(
        @Selector String dalName,
        @Selector String operation,
        @Nullable String from,
        @Nullable String to
    ) {
        Range range = resolveRange(from, to);
        DalOperationType operationType = DalOperationType.valueOf(operation.toUpperCase());
        MetricsViews.StatsView stats = MetricsViews.StatsView.of(
            queryService.stats(dalName, operationType, range.from(), range.to()));
        return new MetricsViews.OperationView(
            dalName, operationType, range.from(), range.to(), stats);
    }

    private Range resolveRange(
        @Nullable String from,
        @Nullable String to
    ) {
        Instant now = clock.instant();
        Instant resolvedTo = StringUtils.hasText(to) ? Instant.parse(to) : now;
        Instant resolvedFrom = StringUtils.hasText(from)
            ? Instant.parse(from)
            : resolvedTo.minus(properties.getDefaultRange());
        return new Range(resolvedFrom, resolvedTo);
    }

    private record Range(Instant from, Instant to) {
    }
}
