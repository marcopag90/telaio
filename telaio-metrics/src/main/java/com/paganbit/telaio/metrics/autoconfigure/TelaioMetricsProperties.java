package com.paganbit.telaio.metrics.autoconfigure;

import com.paganbit.telaio.metrics.store.jdbc.JdbcDalMetricsStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Telaio Metrics, bound to the {@code telaio.metrics} prefix.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@ConfigurationProperties("telaio.metrics")
@Validated
public class TelaioMetricsProperties {

    /**
     * Whether the DAL metrics collection is enabled.
     */
    private boolean enabled = true;

    /**
     * Length of one aggregation window. Smaller buckets give finer trends at the cost of more
     * stored rows.
     */
    @NotNull
    private Duration bucketDuration = Duration.ofMinutes(1);

    /**
     * How often completed buckets are flushed to the stores.
     */
    @NotNull
    private Duration flushInterval = Duration.ofMinutes(1);

    @Valid
    private final Histogram histogram = new Histogram();

    @Valid
    private final InMemory inMemory = new InMemory();

    @Valid
    private final Jdbc jdbc = new Jdbc();

    @Valid
    private final Micrometer micrometer = new Micrometer();

    @Valid
    private final InHouse inHouse = new InHouse();

    @Valid
    private final Endpoint endpoint = new Endpoint();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getBucketDuration() {
        return bucketDuration;
    }

    public void setBucketDuration(Duration bucketDuration) {
        this.bucketDuration = bucketDuration;
    }

    public Duration getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(Duration flushInterval) {
        this.flushInterval = flushInterval;
    }

    public Histogram getHistogram() {
        return histogram;
    }

    public InMemory getInMemory() {
        return inMemory;
    }

    public Jdbc getJdbc() {
        return jdbc;
    }

    public Micrometer getMicrometer() {
        return micrometer;
    }

    public InHouse getInHouse() {
        return inHouse;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Latency histogram scale settings. Changing them makes previously persisted histograms
     * incomparable: old buckets keep contributing counts and min/max/mean, but are skipped for
     * percentiles.
     */
    public static class Histogram {

        /**
         * Upper bound of the first histogram bucket.
         */
        @NotNull
        private Duration firstUpperBound = Duration.ofMillis(1);

        /**
         * Geometric growth factor between consecutive bucket bounds.
         */
        @DecimalMin(value = "1.0", inclusive = false)
        private double growthFactor = 2.0;

        /**
         * Total number of histogram buckets, including the overflow bucket.
         */
        @Min(2)
        private int bucketCount = 20;

        /**
         * Quantiles computed when merging histogram buckets into statistics. Each value must be in
         * {@code (0.0, 1.0]}; for example {@code 0.99} gives the 99th percentile latency.
         */
        @NotEmpty
        private List<@DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") Double> quantiles =
            new ArrayList<>(List.of(0.5, 0.9, 0.95, 0.99));

        public Duration getFirstUpperBound() {
            return firstUpperBound;
        }

        public void setFirstUpperBound(Duration firstUpperBound) {
            this.firstUpperBound = firstUpperBound;
        }

        public double getGrowthFactor() {
            return growthFactor;
        }

        public void setGrowthFactor(double growthFactor) {
            this.growthFactor = growthFactor;
        }

        public int getBucketCount() {
            return bucketCount;
        }

        public void setBucketCount(int bucketCount) {
            this.bucketCount = bucketCount;
        }

        public List<Double> getQuantiles() {
            return quantiles;
        }

        public void setQuantiles(List<Double> quantiles) {
            this.quantiles = quantiles;
        }
    }

    /**
     * Settings for the in-memory fallback store, used when no persistent store is available.
     */
    public static class InMemory {

        /**
         * How long buckets are retained.
         */
        @NotNull
        private Duration retention = Duration.ofHours(24);

        /**
         * Hard cap on retained buckets; the oldest are evicted first.
         */
        @Positive
        private int maxBuckets = 10_000;

        public Duration getRetention() {
            return retention;
        }

        public void setRetention(Duration retention) {
            this.retention = retention;
        }

        public int getMaxBuckets() {
            return maxBuckets;
        }

        public void setMaxBuckets(int maxBuckets) {
            this.maxBuckets = maxBuckets;
        }
    }

    /**
     * Settings for the JDBC store.
     */
    public static class Jdbc {

        /**
         * Whether the JDBC store is used when a DataSource is available.
         */
        private boolean enabled = true;

        /**
         * Name of the metrics table. Must be a plain, unqualified SQL identifier, since it is
         * interpolated into the store's SQL (identifiers cannot be bound as parameters). No schema
         * prefix: the shipped DDL creates the table unqualified, so the schema is resolved by the
         * DataSource connection's default schema.
         */
        @NotBlank
        @Pattern(
            regexp = JdbcDalMetricsStore.TABLE_NAME_PATTERN,
            message = "must be a plain, unqualified SQL identifier (e.g. telaio_metrics_bucket)")
        private String tableName = "telaio_metrics_bucket";

        /**
         * Schema initialization mode: {@code embedded} creates the table only on embedded
         * databases, {@code always} on any database, {@code never} leaves schema management to
         * the application (the shipped per-vendor scripts serve as reference DDL).
         */
        @NotNull
        private SchemaInitialization initializeSchema = SchemaInitialization.EMBEDDED;

        /**
         * How long persisted buckets are retained; older rows are deleted periodically
         * (see {@link #cleanupInterval}).
         */
        @NotNull
        private Duration retention = Duration.ofDays(7);

        /**
         * How often expired rows are deleted. Cleanup is throttled to at most once per interval per
         * instance (piggybacked on flushes), so scaling to N instances yields N independent deletes
         * per interval instead of one per flush — avoiding cross-instance lock contention on the
         * shared table. The delete operation sweeps every instance's expired rows, so rows orphaned by a
         * crashed instance are still reclaimed.
         */
        @NotNull
        private Duration cleanupInterval = Duration.ofHours(1);

        /**
         * Database platform used to resolve the schema script (e.g. {@code postgresql}).
         * Auto-detected from the DataSource when unset.
         */
        private @Nullable String platform;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public SchemaInitialization getInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(SchemaInitialization initializeSchema) {
            this.initializeSchema = initializeSchema;
        }

        public Duration getRetention() {
            return retention;
        }

        public void setRetention(Duration retention) {
            this.retention = retention;
        }

        public Duration getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(Duration cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }

        public @Nullable String getPlatform() {
            return platform;
        }

        public void setPlatform(@Nullable String platform) {
            this.platform = platform;
        }

        /**
         * Schema initialization modes, mirroring the Spring convention used by Session, Batch,
         * and Quartz JDBC stores.
         */
        public enum SchemaInitialization {
            /**
             * Initialize the schema only when the DataSource is an embedded database.
             */
            EMBEDDED,
            /**
             * Always initialize the schema.
             */
            ALWAYS,
            /**
             * Never initialize the schema.
             */
            NEVER
        }
    }

    /**
     * Settings for the Micrometer recorder.
     * Opt-in and, when active, supersedes the in-house path
     * (see {@link InHouse}).
     */
    public static class Micrometer {

        /**
         * Whether DAL timings are recorded into a Micrometer {@code MeterRegistry}. Requires
         * Micrometer on the classpath and a {@code MeterRegistry} bean. Off by default — activation
         * is explicit rather than keyed off classpath presence, since Spring Boot auto-creates a
         * registry whenever Micrometer is present.
         */
        private boolean enabled = false;

        /**
         * Name of the Micrometer {@code Timer}, tagged with {@code dal}, {@code operation}, and
         * {@code outcome}. The configured Micrometer registry maps it to its backend's own naming
         * convention.
         */
        @NotBlank
        private String metricName = "telaio.dal.operation";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMetricName() {
            return metricName;
        }

        public void setMetricName(String metricName) {
            this.metricName = metricName;
        }
    }

    /**
     * Controls the in-house collection path (aggregator, flush scheduler, store, and the
     * {@code telaiometrics} endpoint).
     */
    public static class InHouse {

        /**
         * Whether the in-house path is active. Unset (the default) means "on, unless the Micrometer
         * recorder takes over" — set it explicitly to {@code true} to run both paths during a
         * transition, or to {@code false} to disable the in-house path outright. The dynamic
         * default is resolved by {@code OnInHouseMetricsCondition}.
         */
        private @Nullable Boolean enabled;

        public @Nullable Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(@Nullable Boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Settings for the {@code telaiometrics} actuator endpoint.
     */
    public static class Endpoint {

        /**
         * Time range used when a request does not specify {@code from}/{@code to}.
         */
        @NotNull
        private Duration defaultRange = Duration.ofHours(1);

        /**
         * Default size of the slowest-operations ranking in the overview.
         */
        @Positive
        private int topN = 10;

        public Duration getDefaultRange() {
            return defaultRange;
        }

        public void setDefaultRange(Duration defaultRange) {
            this.defaultRange = defaultRange;
        }

        public int getTopN() {
            return topN;
        }

        public void setTopN(int topN) {
            this.topN = topN;
        }
    }
}
