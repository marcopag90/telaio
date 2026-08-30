package com.paganbit.telaio.metrics.store.jdbc;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.metrics.model.DalMetricsBucket;
import com.paganbit.telaio.metrics.store.DalMetricsBucketMerger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the failure paths of {@link JdbcDalMetricsStore} that an embedded database cannot
 * provoke deterministically: a duplicate-key collision whose row has vanished by the time it is
 * re-read, and a failing retention cleanup — which must never break the flush that triggered it.
 */
class JdbcDalMetricsStoreFallbackTest {

    private static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");
    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DalMetricsBucketMerger merger = mock(DalMetricsBucketMerger.class);
    private final Clock clock = mock(Clock.class);

    private final DalMetricsBucket bucket = new DalMetricsBucket(NOW.minus(Duration.ofMinutes(5)),
        Duration.ofMinutes(1), "products", DalOperationType.READ, 5, 0, 0, 5_000_000, 1_000_000, 1_000_000,
        new long[]{0, 5, 0, 0});

    private JdbcDalMetricsStore store() {
        return new JdbcDalMetricsStore(
            jdbcTemplate, merger, "telaio_metrics_bucket", Duration.ofDays(7), null, CLEANUP_INTERVAL, clock);
    }

    @Test
    @SuppressWarnings("unchecked")
    void store_whenCollidingRowVanishedBeforeReRead_shouldInsertAgain() {
        when(clock.instant()).thenReturn(NOW);
        // The INSERT collides, but the row is gone when re-read (e.g. swept by a concurrent cleanup).
        when(jdbcTemplate.update(startsWith("INSERT"), any(Object[].class)))
            .thenThrow(new DuplicateKeyException("duplicate key"))
            .thenReturn(1);
        when(jdbcTemplate.query(startsWith("SELECT"), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        store().store(List.of(bucket));

        verify(jdbcTemplate, times(2)).update(startsWith("INSERT"), any(Object[].class));
        verify(jdbcTemplate, never()).update(startsWith("UPDATE"), any(Object[].class));
    }

    @Test
    void store_whenExpiredCleanupFails_shouldStillCompleteTheFlush() {
        // Constructed at NOW; the flush happens a full cleanup interval later, so the sweep is due.
        when(clock.instant()).thenReturn(NOW, NOW.plus(CLEANUP_INTERVAL).plusSeconds(1));
        when(jdbcTemplate.update(startsWith("INSERT"), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.update(startsWith("DELETE"), any(Object[].class)))
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        JdbcDalMetricsStore store = store();

        assertThatCode(() -> store.store(List.of(bucket))).doesNotThrowAnyException();

        verify(jdbcTemplate).update(startsWith("INSERT"), any(Object[].class));
        verify(jdbcTemplate).update(startsWith("DELETE"), any(Object[].class));
    }
}
