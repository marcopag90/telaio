package io.paganbit.telaio.metrics.endpoint;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.autoconfigure.TelaioMetricsProperties;
import io.paganbit.telaio.metrics.model.DalMetricsStats;
import io.paganbit.telaio.metrics.store.DalMetricsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelaioMetricsEndpointTest {

    private static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");

    @Mock
    private DalMetricsQueryService queryService;
    @Captor
    private ArgumentCaptor<Instant> fromCaptor;
    @Captor
    private ArgumentCaptor<Instant> toCaptor;

    private TelaioMetricsEndpoint endpoint;

    @BeforeEach
    void setUp() {
        TelaioMetricsProperties.Endpoint properties = new TelaioMetricsProperties.Endpoint();
        properties.setDefaultRange(Duration.ofHours(1));
        properties.setTopN(10);
        endpoint = new TelaioMetricsEndpoint(queryService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DalMetricsStats stats(String dal, DalOperationType op, long count, long meanNanos) {
        return new DalMetricsStats(dal, op, NOW.minus(Duration.ofHours(1)), NOW, count, 0,
            Duration.ofNanos(meanNanos * count), Duration.ofNanos(meanNanos),
            Duration.ofNanos(meanNanos), Duration.ofNanos(meanNanos),
            Map.of(0.5, Duration.ofNanos(meanNanos), 0.9, Duration.ofNanos(meanNanos),
                0.95, Duration.ofNanos(meanNanos), 0.99, Duration.ofNanos(meanNanos)));
    }

    @Test
    void overview_shouldUseDefaultRangeWhenUnspecified() {
        when(queryService.dalNames(any(), any())).thenReturn(List.of("products"));
        when(queryService.stats(eq("products"), eq(null), any(), any()))
            .thenReturn(stats("products", null, 5, 2_000_000));
        when(queryService.topSlowest(eq(10), any(), any())).thenReturn(List.of());

        MetricsViews.OverviewView overview = endpoint.overview(null, null, null);

        assertThat(overview.to()).isEqualTo(NOW);
        assertThat(overview.from()).isEqualTo(NOW.minus(Duration.ofHours(1)));
        assertThat(overview.dals()).hasSize(1);
        assertThat(overview.dals().getFirst().stats().count()).isEqualTo(5);
        assertThat(overview.dals().getFirst().stats().meanMs()).isEqualTo(2.0);
    }

    @Test
    void overview_shouldHonourExplicitRangeAndTop() {
        when(queryService.dalNames(any(), any())).thenReturn(List.of());
        when(queryService.topSlowest(eq(3), fromCaptor.capture(), toCaptor.capture()))
            .thenReturn(List.of());

        endpoint.overview("2026-06-12T00:00:00Z", "2026-06-12T06:00:00Z", 3);

        assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2026-06-12T00:00:00Z"));
        assertThat(toCaptor.getValue()).isEqualTo(Instant.parse("2026-06-12T06:00:00Z"));
    }

    @Test
    void dal_shouldReturnOverallAndPerOperationStats() {
        when(queryService.statsByOperation(eq("products"), any(), any()))
            .thenReturn(Map.of(DalOperationType.READ, stats("products", DalOperationType.READ, 3, 1_000_000)));
        when(queryService.stats(eq("products"), eq(null), any(), any()))
            .thenReturn(stats("products", null, 3, 1_000_000));

        MetricsViews.DalView view = endpoint.dal("products", null, null);

        assertThat(view.dalName()).isEqualTo("products");
        assertThat(view.overall().count()).isEqualTo(3);
        assertThat(view.operations()).containsKey(DalOperationType.READ);
    }

    @Test
    void operation_shouldParseOperationNameCaseInsensitively() {
        when(queryService.stats(eq("products"), eq(DalOperationType.READ), any(), any()))
            .thenReturn(stats("products", DalOperationType.READ, 7, 3_000_000));

        MetricsViews.OperationView view = endpoint.operation("products", "read", null, null);

        assertThat(view.operation()).isEqualTo(DalOperationType.READ);
        assertThat(view.stats().count()).isEqualTo(7);
        assertThat(view.stats().meanMs()).isEqualTo(3.0);
        assertThat(view.stats().percentilesMs()).containsKey("p95");
    }

    @Test
    void operation_shouldReportZerosWhenNoData() {
        when(queryService.stats(eq("products"), eq(DalOperationType.DELETE), any(), any()))
            .thenReturn(new DalMetricsStats("products", DalOperationType.DELETE,
                NOW.minus(Duration.ofHours(1)), NOW, 0, 0, Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO, Map.of()));

        MetricsViews.OperationView view = endpoint.operation("products", "DELETE", null, null);

        assertThat(view.stats().count()).isZero();
        assertThat(view.stats().meanMs()).isZero();
    }

    @Test
    void overview_shouldRankSlowestWithMillisecondDurations() {
        when(queryService.dalNames(any(), any())).thenReturn(List.of());
        when(queryService.topSlowest(eq(10), any(), any())).thenReturn(List.of(
            stats("orders", DalOperationType.READ, 10, 5_000_000),
            stats("products", DalOperationType.READ, 10, 2_000_000)));

        MetricsViews.OverviewView overview = endpoint.overview(null, null, null);

        assertThat(overview.slowest()).hasSize(2);
        assertThat(overview.slowest().getFirst().dalName()).isEqualTo("orders");
        assertThat(overview.slowest().getFirst().stats().meanMs()).isEqualTo(5.0);
    }
}
