package io.paganbit.telaio.metrics.store.jdbc;

import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.metrics.model.DalMetricsBucket;
import io.paganbit.telaio.metrics.model.DalMetricsStats;
import io.paganbit.telaio.metrics.store.DalMetricsBucketMerger;
import io.paganbit.telaio.metrics.store.DalMetricsQueryService;
import io.paganbit.telaio.metrics.store.DalMetricsStore;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * JDBC-backed metrics store, the default persistent store when a {@link javax.sql.DataSource} is
 * available.
 *
 * <p>Each application instance owns its rows through a per-start {@code instanceId} embedded in
 * the primary key, so concurrent instances never contend: writes are plain ANSI {@code INSERT}s
 * with no vendor-specific upsert syntax. The only collision is the rare re-store of a key within
 * one instance (a late-recorded cell, or the partial bucket flushed at shutdown), handled by a
 * read-merge-update fallback that is safe because a single flusher thread writes per instance.
 * Cross-instance aggregation is free: the query side merges all matching rows.</p>
 *
 * <p>All durations are stored in nanoseconds and the latency histogram as a comma-separated list
 * of counts. Statistics are computed by fetching the matching rows and merging them in Java
 * through {@link DalMetricsBucketMerger}, the same path used by the in-memory store.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class JdbcDalMetricsStore implements DalMetricsStore, DalMetricsQueryService {

    private static final Logger log = LoggerFactory.getLogger(JdbcDalMetricsStore.class);

    /**
     * Allowed shape for the configured {@code tableName}: a plain, unqualified SQL identifier. The
     * name comes from trusted application configuration ({@code telaio.metrics.jdbc.table-name}) and
     * is interpolated into the SQL, since JDBC has no parameter binding for identifiers. It is
     * validated up front purely to fail fast with a clear message on a misconfigured name, instead
     * of surfacing an opaque SQL syntax error on the first query. Exposed so
     * {@code TelaioMetricsProperties} can enforce the same shape at config-binding time.
     *
     * <p>No schema prefix is accepted: the shipped DDL scripts create the table unqualified, so the
     * schema is resolved by the connection's default schema (a {@link javax.sql.DataSource} concern),
     * not baked into the table name.</p>
     */
    public static final String TABLE_NAME_PATTERN = "^[A-Za-z_]\\w*$";

    private static final Pattern SAFE_TABLE_NAME = Pattern.compile(TABLE_NAME_PATTERN);

    private final JdbcTemplate jdbcTemplate;
    private final DalMetricsBucketMerger merger;
    private final String tableName;
    private final Duration retention;
    private final String instanceId;
    private final @Nullable TransactionTemplate transactionTemplate;
    private final Duration cleanupInterval;
    private final Clock clock;
    private final AtomicReference<Instant> lastCleanup = new AtomicReference<>();

    private final RowMapper<DalMetricsBucket> bucketMapper = (rs, rowNum) -> new DalMetricsBucket(
        rs.getObject("bucket_start", LocalDateTime.class).toInstant(ZoneOffset.UTC),
        Duration.ofMillis(rs.getLong("bucket_duration_ms")),
        rs.getString("dal_name"),
        DalOperationType.valueOf(rs.getString("operation")),
        rs.getLong("invocation_count"),
        rs.getLong("error_count"),
        rs.getLong("client_error_count"),
        rs.getLong("total_duration_nanos"),
        rs.getLong("min_duration_nanos"),
        rs.getLong("max_duration_nanos"),
        parseHistogram(rs.getString("histogram_counts"))
    );

    public JdbcDalMetricsStore(
        JdbcTemplate jdbcTemplate,
        DalMetricsBucketMerger merger,
        String tableName,
        Duration retention,
        @Nullable TransactionTemplate transactionTemplate,
        Duration cleanupInterval,
        Clock clock
    ) {
        if (!SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("""
                Invalid 'telaio.metrics.jdbc.table-name' value: '%s'. Expected a plain, unqualified \
                SQL identifier (e.g. 'telaio_metrics_bucket').""".formatted(tableName));
        }
        this.jdbcTemplate = jdbcTemplate;
        this.merger = merger;
        this.tableName = tableName;
        this.retention = retention;
        this.transactionTemplate = transactionTemplate;
        this.cleanupInterval = cleanupInterval;
        this.clock = clock;
        this.instanceId = UUID.randomUUID().toString();
        // Defer the first cleanup by one full interval; this also avoids a cold-start DELETE burst
        // when many instances boot together.
        this.lastCleanup.set(clock.instant());
    }

    @Override
    public void store(List<DalMetricsBucket> buckets) {
        // Intentionally row-by-row, not batchUpdate(): a flush carries only a handful of buckets (one
        // per active dal+operation window) on a background thread, so batching saves nothing
        // measurable; and a batch that partially fails would force a re-merge of already-inserted
        // rows, doubling counts. The rare same-instance key collision is handled by the
        // read-merge-update fallback.
        for (DalMetricsBucket bucket : buckets) {
            try {
                insert(bucket);
            } catch (DuplicateKeyException alreadyStored) {
                mergeExisting(bucket);
            }
        }
        deleteExpiredIfDue();
    }

    private void deleteExpiredIfDue() {
        Instant now = clock.instant();
        if (Duration.between(lastCleanup.get(), now).compareTo(cleanupInterval) >= 0) {
            lastCleanup.set(now);
            deleteExpired();
        }
    }

    @SuppressWarnings("squid:S2077")
    private void insert(DalMetricsBucket bucket) {
        String sql = """
            INSERT INTO %s (
                bucket_start,
                bucket_duration_ms,
                dal_name,
                operation,
                instance_id,
                invocation_count,
                error_count,
                client_error_count,
                total_duration_nanos,
                min_duration_nanos,
                max_duration_nanos,
                histogram_counts
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(tableName);
        jdbcTemplate.update(sql,
            toDbTimestamp(bucket.bucketStart()),
            bucket.bucketDuration().toMillis(),
            bucket.dalName(),
            bucket.operation().name(),
            instanceId,
            bucket.count(),
            bucket.errorCount(),
            bucket.clientErrorCount(),
            bucket.totalDurationNanos(),
            bucket.minDurationNanos(),
            bucket.maxDurationNanos(),
            formatHistogram(bucket.histogramCounts()));
    }

    private void mergeExisting(DalMetricsBucket incoming) {
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> doMergeExisting(incoming));
        } else {
            doMergeExisting(incoming);
        }
    }

    @SuppressWarnings("squid:S2077")
    private void doMergeExisting(DalMetricsBucket incoming) {
        String selectSql = """
            SELECT *
            FROM %s
            WHERE bucket_start = ?
              AND dal_name = ?
              AND operation = ?
              AND instance_id = ?
            """.formatted(tableName);
        List<DalMetricsBucket> existing = jdbcTemplate.query(selectSql, bucketMapper,
            toDbTimestamp(incoming.bucketStart()), incoming.dalName(),
            incoming.operation().name(), instanceId);
        if (existing.isEmpty()) {
            insert(incoming);
            return;
        }
        DalMetricsBucket merged = existing.getFirst().add(incoming);
        String updateSql = """
            UPDATE %s
            SET invocation_count = ?,
                error_count = ?,
                client_error_count = ?,
                total_duration_nanos = ?,
                min_duration_nanos = ?,
                max_duration_nanos = ?,
                histogram_counts = ?
            WHERE bucket_start = ?
              AND dal_name = ?
              AND operation = ?
              AND instance_id = ?
            """.formatted(tableName);
        jdbcTemplate.update(updateSql,
            merged.count(), merged.errorCount(), merged.clientErrorCount(), merged.totalDurationNanos(),
            merged.minDurationNanos(), merged.maxDurationNanos(),
            formatHistogram(merged.histogramCounts()),
            toDbTimestamp(incoming.bucketStart()), incoming.dalName(),
            incoming.operation().name(), instanceId);
    }

    @SuppressWarnings("squid:S2077")
    private void deleteExpired() {
        String sql = """
            DELETE FROM %s
            WHERE bucket_start < ?
            """.formatted(tableName);
        try {
            Instant cutoff = clock.instant().minus(retention);
            jdbcTemplate.update(sql, toDbTimestamp(cutoff));
        } catch (Exception e) {
            log.warn("Failed to delete expired DAL metrics buckets", e);
        }
    }

    @Override
    @SuppressWarnings("squid:S2077")
    public List<String> dalNames(Instant from, Instant to) {
        String sql = """
            SELECT DISTINCT dal_name
            FROM %s
            WHERE bucket_start >= ? AND bucket_start < ?
            ORDER BY dal_name
            """.formatted(tableName);
        return jdbcTemplate.queryForList(sql, String.class, toDbTimestamp(from), toDbTimestamp(to));
    }

    @Override
    public DalMetricsStats stats(String dalName, @Nullable DalOperationType operation, Instant from, Instant to) {
        return merger.merge(dalName, operation, from, to, findBuckets(dalName, operation, from, to));
    }

    @Override
    public Map<DalOperationType, DalMetricsStats> statsByOperation(String dalName, Instant from, Instant to) {
        Map<DalOperationType, List<DalMetricsBucket>> byOperation = new EnumMap<>(DalOperationType.class);
        for (DalMetricsBucket bucket : findBuckets(dalName, null, from, to)) {
            byOperation.computeIfAbsent(bucket.operation(), k -> new ArrayList<>()).add(bucket);
        }
        Map<DalOperationType, DalMetricsStats> result = new EnumMap<>(DalOperationType.class);
        byOperation.forEach((operation, buckets) ->
            result.put(operation, merger.merge(dalName, operation, from, to, buckets)));
        return result;
    }

    @Override
    public List<DalMetricsStats> topSlowest(int limit, Instant from, Instant to) {
        record DalAndOperation(String dalName, DalOperationType operation) {
        }
        Map<DalAndOperation, List<DalMetricsBucket>> grouped = new LinkedHashMap<>();
        for (DalMetricsBucket bucket : findBuckets(null, null, from, to)) {
            grouped.computeIfAbsent(new DalAndOperation(bucket.dalName(), bucket.operation()),
                k -> new ArrayList<>()).add(bucket);
        }
        return grouped.entrySet().stream()
            .map(entry -> merger.merge(
                entry.getKey().dalName(), entry.getKey().operation(), from, to, entry.getValue()))
            .filter(stats -> stats.count() > 0)
            .sorted(Comparator.comparing(DalMetricsStats::mean).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    @SuppressWarnings("squid:S2077")
    public List<DalMetricsBucket> findBuckets(
        @Nullable String dalName,
        @Nullable DalOperationType operation,
        Instant from,
        Instant to
    ) {
        List<String> conditions = new ArrayList<>(List.of("bucket_start >= ?", "bucket_start < ?"));
        List<Object> args = new ArrayList<>();
        args.add(toDbTimestamp(from));
        args.add(toDbTimestamp(to));
        if (dalName != null) {
            conditions.add("dal_name = ?");
            args.add(dalName);
        }
        if (operation != null) {
            conditions.add("operation = ?");
            args.add(operation.name());
        }
        String sql = """
            SELECT *
            FROM %s
            WHERE %s
            ORDER BY bucket_start
            """.formatted(tableName, String.join(" AND ", conditions));
        return jdbcTemplate.query(sql, bucketMapper, args.toArray());
    }

    private static LocalDateTime toDbTimestamp(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String formatHistogram(long[] counts) {
        return Arrays.stream(counts).mapToObj(Long::toString).collect(Collectors.joining(","));
    }

    private static long[] parseHistogram(@Nullable String csv) {
        if (csv == null || csv.isEmpty()) {
            return new long[0];
        }
        String[] parts = csv.split(",");
        long[] counts = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            counts[i] = Long.parseLong(parts[i].trim());
        }
        return counts;
    }
}
