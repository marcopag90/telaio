package com.paganbit.telaio.metrics.model;

import com.paganbit.telaio.core.adapter.DalOperationType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DalMetricsStatsTest {

    private static final Instant FROM = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-12T11:00:00Z");

    @Test
    void fields_shouldBeAccessibleViaAccessors() {
        Map<Double, Duration> percentiles = Map.of(0.5, Duration.ofMillis(2), 0.99, Duration.ofMillis(8));
        DalMetricsStats stats = new DalMetricsStats(
            "products", DalOperationType.READ, FROM, TO,
            100, 5, 3,
            Duration.ofSeconds(10), Duration.ofMillis(1), Duration.ofMillis(9),
            Duration.ofMillis(5),
            percentiles
        );

        assertThat(stats.dalName()).isEqualTo("products");
        assertThat(stats.operation()).isEqualTo(DalOperationType.READ);
        assertThat(stats.from()).isEqualTo(FROM);
        assertThat(stats.to()).isEqualTo(TO);
        assertThat(stats.count()).isEqualTo(100);
        assertThat(stats.errorCount()).isEqualTo(5);
        assertThat(stats.clientErrorCount()).isEqualTo(3);
        assertThat(stats.totalDuration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(stats.min()).isEqualTo(Duration.ofMillis(1));
        assertThat(stats.max()).isEqualTo(Duration.ofMillis(9));
        assertThat(stats.mean()).isEqualTo(Duration.ofMillis(5));
        assertThat(stats.percentiles()).isEqualTo(percentiles);
    }

    @Test
    void operation_shouldAllowNull() {
        DalMetricsStats stats = new DalMetricsStats(
            "products", null, FROM, TO,
            0, 0, 0,
            Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
            Map.of()
        );

        assertThat(stats.operation()).isNull();
    }

    @Test
    void equals_shouldConsiderAllFields() {
        Map<Double, Duration> p = Map.of(0.5, Duration.ofMillis(2));
        DalMetricsStats a = new DalMetricsStats("products", DalOperationType.READ, FROM, TO,
            10, 1, 2, Duration.ofSeconds(1), Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(3), p);
        DalMetricsStats b = new DalMetricsStats("products", DalOperationType.READ, FROM, TO,
            10, 1, 2, Duration.ofSeconds(1), Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(3), p);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).hasSameHashCodeAs(b);
    }
}
