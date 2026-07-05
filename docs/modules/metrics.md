# Telaio: Metrics Module

The metrics module collects performance statistics for every DAL operation: counts, error rates, and latency
percentiles. Metrics are **ON by default** via `@DalMetrics`.

## Purpose

- **Performance monitoring:** Measure operation latency (min, max, mean, percentiles)
- **Reliability tracking:** Count successes and errors per operation
- **Multi-vendor JDBC persistence:** Optional persistent storage (PostgreSQL, MySQL, MariaDB, Oracle, SQL Server)
- **Micrometer integration:** Export metrics to Prometheus, CloudWatch, Datadog, etc.
- **Actuator endpoint:** Built-in `/actuator/telaiometrics` for ad-hoc queries
- **On by default:** Metrics are collected unless explicitly disabled

## Key Public Types

### Annotations

| Annotation    | Target | Purpose                                                                                                                                        |
|---------------|--------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `@DalMetrics` | Class  | Tunes metrics collection. Attributes: `enabled` (default true), `operations` (default all). Use `@DalMetrics(enabled=false)` to exclude a DAL. |

### Core Types

| Type                    | Purpose                                                                                                      |
|-------------------------|--------------------------------------------------------------------------------------------------------------|
| `DalMetricsInterceptor` | Channel-agnostic: intercepts every DAL operation to measure latency and count outcomes (SUCCESS / CLIENT_ERROR for validation, not-found and conflicts / ERROR for service faults). Classification follows core's fixed `DalFailureKind` taxonomy — unlike audit there is no classifier SPI; to customize it, replace the `DalMetricsInterceptorProvider` bean |
| `DalMetricsRecorder`    | SPI: sink for recording individual operation timings (aggregator + optional Micrometer)                      |
| `DalMetricsAggregator`  | Lock-free in-memory accumulator: buckets timings by 1-minute windows (configurable) and computes percentiles |

### Storage

| Type                      | Purpose                                                                                        |
|---------------------------|------------------------------------------------------------------------------------------------|
| `DalMetricsStore`         | SPI for persisting completed metric buckets. Implementations:                                  |
| `InMemoryDalMetricsStore` | Built-in: zero-config in-memory store (24-hour retention by default)                           |
| `JdbcDalMetricsStore`     | Built-in: persistent JDBC store (multi-vendor: PostgreSQL, MySQL, MariaDB, Oracle, SQL Server) |
| `DalMetricsQueryService`  | SPI: read interface for querying metrics over time                                             |

### Querying

| Type                    | Purpose                                                                        |
|-------------------------|--------------------------------------------------------------------------------|
| `TelaioMetricsEndpoint` | Actuator endpoint (`telaiometrics`): HTTP + JMX interface for querying metrics |

## How Developers Use It

### Metrics Are On by Default

```java
@DalService(name = "products")
public class ProductDalService extends JpaDal<Product, Long> {
}
```

Metrics are automatically collected for all operations.

### Disable Metrics for a DAL

```java
@DalService(name = "announcements")
@DalMetrics(enabled = false)
public class AnnouncementDalService extends JpaDal<Announcement, Long> {
}
```

### Measure Only Specific Operations

```java
@DalService(name = "products")
@DalMetrics(operations = {DalOperationType.CREATE, DalOperationType.UPDATE})
public class ProductDalService extends JpaDal<Product, Long> {
}
```

Only create and update are measured; reads are not.

### Query Metrics via the Actuator Endpoint

```bash
# Overview of all DALs (slowest operations in the last 1 hour)
curl http://localhost:8080/actuator/telaiometrics

# Metrics for a specific DAL over a custom range (DAL name is a path segment)
curl 'http://localhost:8080/actuator/telaiometrics/products?from=2026-07-02T09:00:00Z&to=2026-07-02T11:00:00Z'

# Metrics for a specific DAL + operation (both are path segments; the operation segment is case-insensitive)
curl 'http://localhost:8080/actuator/telaiometrics/products/update'
```

## Configuration

### Properties (prefix `telaio.metrics`)

| Property         | Type     | Default  | Purpose                                             |
|------------------|----------|----------|-----------------------------------------------------|
| `enabled`        | Boolean  | `true`   | Global switch: disable metrics collection entirely  |
| `bucket-duration` | Duration | 1 minute | Length of one aggregation window                    |
| `flush-interval`  | Duration | 1 minute | How often completed buckets are persisted to stores |

#### Histogram (latency percentiles)

| Property                    | Type         | Default                  | Purpose                                                   |
|-----------------------------|--------------|--------------------------|-----------------------------------------------------------|
| `histogram.first-upper-bound` | Duration     | 1 ms                     | Upper bound of the first histogram bucket                 |
| `histogram.growth-factor`    | Double       | 2.0                      | Geometric growth factor between consecutive bucket bounds |
| `histogram.bucket-count`     | Integer      | 20                       | Total number of histogram buckets                         |
| `histogram.quantiles`       | List<Double> | `[0.5, 0.9, 0.95, 0.99]` | Percentiles to compute (50th, 90th, 95th, 99th)           |

#### In-Memory Store

| Property              | Type     | Default  | Purpose                                 |
|-----------------------|----------|----------|-----------------------------------------|
| `in-memory.retention` | Duration | 24 hours | How long buckets are retained in memory |
| `in-memory.max-buckets` | Integer  | 10,000   | Hard cap on retained buckets            |

#### JDBC Persistence

| Property                | Type                    | Default                 | Purpose                                                                                                           |
|-------------------------|-------------------------|-------------------------|-------------------------------------------------------------------------------------------------------------------|
| `jdbc.enabled`          | Boolean                 | `true`                  | Whether to use the JDBC store when a DataSource is available                                                      |
| `jdbc.table-name`        | String                  | `telaio_metrics_bucket` | Table name for storing metric buckets                                                                             |
| `jdbc.initialize-schema` | EMBEDDED, ALWAYS, NEVER | EMBEDDED                | Schema initialization: `EMBEDDED` (H2/tests only), `ALWAYS` (all), `NEVER` (application-managed)                  |
| `jdbc.retention`        | Duration                | 7 days                  | How long persisted buckets are retained                                                                           |
| `jdbc.cleanup-interval`  | Duration                | 1 hour                  | How often expired rows are deleted                                                                                |
| `jdbc.platform`         | String                  | auto-detected           | Database platform: `postgresql`, `mysql`, `mariadb`, `oracle`, `sqlserver`. Auto-detected from driver when unset. |

#### Micrometer Export (Opt-In)

| Property                | Type    | Default                | Purpose                                                                                                                                                    |
|-------------------------|---------|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `micrometer.enabled`    | Boolean | `false`                | Whether to record timings to Micrometer `MeterRegistry`. Off by default; requires Micrometer on the classpath. When enabled, supersedes the in-house path. |
| `micrometer.metric-name` | String  | `telaio.dal.operation` | Micrometer metric name (tagged with `dal`, `operation`, `outcome`)                                                                                         |

#### In-House Path Control

| Property          | Type    | Default     | Purpose                                                                                                                                                           |
|-------------------|---------|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `in-house.enabled` | Boolean | null (auto) | Whether the in-house collection path is active. `null` (default) = "on unless Micrometer takes over"; `true` = "run both"; `false` = "disable in-house entirely". |

#### Actuator Endpoint

| Property                | Type     | Default | Purpose                                        |
|-------------------------|----------|---------|------------------------------------------------|
| `endpoint.default-range` | Duration | 1 hour  | Time range when request omits `from`/`to`      |
| `endpoint.top-n`         | Integer  | 10      | Default size of the slowest-operations ranking |

### Example application.yaml

```yaml
telaio:
  metrics:
    enabled: true
    bucket-duration: 1m
    jdbc:
      enabled: true
      initialize-schema: always
      platform: postgresql
      retention: 7d
    micrometer:
      enabled: false
```

## Supported JDBC Platforms

Metrics module is tested against:

- **PostgreSQL 17**
- **MySQL 8.4**
- **MariaDB 11.4**
- **Oracle 21c**
- **SQL Server 2022**

H2 is supported for tests and development only.

## See Also

- [Observability Guide](../observability.md) — Metrics + Audit together
- [Configuration Reference](../configuration.md) — All metrics properties
- [Architecture](../architecture.md) — Metrics' place in interception
- [Core Module](./core.md) — The DAL contract
