package com.paganbit.telaio.metrics.endpoint;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the small formatting helpers of {@link MetricsViews}.
 */
class MetricsViewsTest {

    @Test
    void quantileKey_shouldRenderPercentileLabels() {
        assertThat(MetricsViews.quantileKey(0.5)).isEqualTo("p50");
        assertThat(MetricsViews.quantileKey(0.99)).isEqualTo("p99");
        assertThat(MetricsViews.quantileKey(0.999)).isEqualTo("p100");
    }

    @Test
    void toMillis_shouldConvertDurationsAndTreatNullAsZero() {
        assertThat(MetricsViews.toMillis(Duration.ofMillis(1500))).isEqualTo(1500.0);
        assertThat(MetricsViews.toMillis(Duration.ofNanos(500_000))).isEqualTo(0.5);
        assertThat(MetricsViews.toMillis(null)).isZero();
    }
}
