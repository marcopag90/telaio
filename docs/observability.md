# Observability Guide

This guide covers Telaio's observability features: audit logging, metrics collection, and integration with monitoring
tools.

## Overview

Telaio provides two complementary observability paths:

1. **Audit**: Record *what happened* — operation, arguments, outcome, principal, duration. Opt-in per DAL via
   `@DalAudit`.
2. **Metrics**: Record *how fast* — latency distribution, success/error rates, throughput. On by default; tune per DAL
   via `@DalMetrics`.

Both apply to every invocation channel (REST, messaging, programmatic), not just REST.

## Audit

### Purpose

Audit creates a durable, queryable trail of DAL operations for compliance, debugging, and security investigation.

### Enabling Audit

Audit is **opt-in**. Declare `@DalAudit` on a DAL service:

```java
@DalService(name = "products")
@DalAudit  // Audit all operations
public class ProductDalService extends JpaDal<Product, Long> {
}
```

To audit specific operations only:

```java
@DalService(name = "products")
@DalAudit(operations = {DalOperationType.CREATE, DalOperationType.UPDATE})  // Audit only writes
public class ProductDalService extends JpaDal<Product, Long> {
}
```

When `@DalAudit` is absent, no auditing is performed.

### Audit Event Schema

Each audit event captures:

- **timestamp**: ISO 8601 (e.g., `2025-07-01T10:30:45.123Z`)
- **event**: `"dal.audit"` — emitted **only** in the JSON format; the TEXT (logfmt) format omits this field
- **dal**: The DAL name (e.g., `"products"`)
- **operation**: The CRUD operation (`CREATE`, `READ`, `READ_ONE`, `UPDATE`, `DELETE`)
- **principal**: Username or principal name (from Spring Security)
- **outcome**: `SUCCESS`, `VALIDATION`, `NOT_FOUND`, `CONFLICT`, `ERROR`, or `DENIED`
- **durationMs**: Latency in milliseconds
- **args**: Per-operation argument snapshot, keyed by parameter role (`input`, `id`, `patch`, `filter`, `pageable`);
  every value is stringified with `String.valueOf(...)`. TEXT flattens the roles to `arg.<role>` fields; JSON nests them
  under `args` with string values.
- **errorType**: Exception class name (present whenever the outcome is not SUCCESS)
- **errorMessage**: Exception message (present whenever the outcome is not SUCCESS)
- **mdc** (optional): Current MDC values if `telaio.audit.logging.include-mdc=true`

### Serialization Formats

#### TEXT Format (Default)

Human-readable logfmt output for development:

```
timestamp=2025-07-01T10:30:45.123Z dal=products operation=CREATE outcome=SUCCESS principal=developer durationMs=15 arg.input="{name=Gaming Laptop, price=2499.99, category=electronics}"
```

**Characteristics**:

- One line per event
- `key=value` pairs with quoting/escaping
- Arguments flattened with `arg.` prefix
- Robust parsing with standard logfmt tooling

#### JSON Format

JSON Lines (one JSON object per line) for log aggregation systems:

```json
{
  "timestamp": "2025-07-01T10:30:45.123Z",
  "event": "dal.audit",
  "dal": "products",
  "operation": "CREATE",
  "outcome": "SUCCESS",
  "principal": "developer",
  "durationMs": 15,
  "args": {
    "input": "{name=Gaming Laptop, price=2499.99, category=electronics}"
  }
}
```

**Characteristics**:

- One JSON object per line
- Nested `args` object (string values, keyed by parameter role)
- Suitable for Elasticsearch, Splunk, Datadog, etc.
- No timestamps/levels/logger names (message is the JSON payload)

### Configuration

```yaml
telaio:
  audit:
    logging:
      format: TEXT                      # or JSON
      category: com.paganbit.telaio.audit.AUDIT
      include-mdc: true                 # Include traceId, spanId, etc.
```

### Logback Setup

By default, audit events are sent to the logger `com.paganbit.telaio.audit.AUDIT`. Configure Logback to route them to a
dedicated appender:

**`logback-spring.xml`** (example for JSON format):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- Default appender for application logs -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Audit appender: plain JSON Lines, no metadata -->
    <appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/audit.log</file>
        <encoder>
            <pattern>%msg%n</pattern>  <!-- IMPORTANT: plain message only -->
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/audit.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>

    <!-- Route audit events to the audit appender, do NOT propagate to root -->
    <logger name="com.paganbit.telaio.audit.AUDIT" level="INFO" additivity="false">
        <appender-ref ref="AUDIT_FILE"/>
    </logger>

    <!-- Root logger for application logs -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>

</configuration>
```

**Key points**:

- The audit appender's `<pattern>` is **`%msg%n`** (message + newline only). Do NOT include timestamp, level, or logger
  name — the JSON event itself is the complete message.
- `additivity="false"` prevents audit logs from propagating to the root logger and appearing in application logs.
- Use a rolling policy to avoid unbounded file growth.
- Do NOT wrap the JSON in another encoder (e.g., LogstashEncoder) — that would nest/escape the already-JSON message.

### Outcomes

Audit events record six outcomes. Client faults (`VALIDATION`, `NOT_FOUND`, `CONFLICT`) are kept
distinct from `ERROR`, so the trail separates misbehaving callers from genuine service faults:

**SUCCESS**: Operation completed without exception.

```json
{
  "outcome": "SUCCESS",
  "durationMs": 45
}
```

**VALIDATION**: The request payload failed validation (client fault, HTTP 400).

```json
{
  "outcome": "VALIDATION",
  "durationMs": 8,
  "errorType": "com.paganbit.telaio.core.exception.DalEntityValidationException",
  "errorMessage": "Validation failed for entity Product"
}
```

**NOT_FOUND**: The target entity does not exist — or is hidden by the DAL's `defaultFilter()`,
which is indistinguishable by design (client fault, HTTP 404). Useful to spot id probing.

```json
{
  "outcome": "NOT_FOUND",
  "durationMs": 5,
  "errorType": "com.paganbit.telaio.core.exception.DalEntityNotFoundException",
  "errorMessage": "Product was not found for id: [999]"
}
```

**CONFLICT**: A versioned entity was modified concurrently (client-retryable fault, HTTP 409).

```json
{
  "outcome": "CONFLICT",
  "durationMs": 11,
  "errorType": "org.springframework.orm.ObjectOptimisticLockingFailureException",
  "errorMessage": "Row was updated or deleted by another transaction"
}
```

**ERROR**: Operation failed because of the service (database down, unexpected exception).

```json
{
  "outcome": "ERROR",
  "durationMs": 12,
  "errorType": "java.sql.SQLException",
  "errorMessage": "Duplicate key value violates unique constraint"
}
```

**DENIED**: Operation was rejected by `DalAuthAdapter` or `DalRbacAdapter` before reaching the DAL bean.

```json
{
  "outcome": "DENIED",
  "principal": "user",
  "durationMs": 2,
  "errorType": "com.paganbit.telaio.security.exception.DalAccessDeniedException",
  "errorMessage": "Access denied"
}
```

The DENIED outcome is useful for security auditing — it shows denied access attempts and who tried
them. `errorType`/`errorMessage` are present whenever the outcome is not `SUCCESS`.

> **Compatibility note for dashboard owners:** the `outcome` value space is extensible — new
> values may be introduced in future versions without changing the field schema (name and string
> type stay stable). Consumers of the trail should tolerate unknown outcome strings.

### MDC Correlation

If `telaio.audit.logging.include-mdc=true`, the current MDC (Mapped Diagnostic Context) is copied onto each event. This
enables correlation with distributed traces:

```json
{
  "timestamp": "2025-07-01T10:30:45.123Z",
  "event": "dal.audit",
  "principal": "developer",
  "outcome": "SUCCESS",
  "mdc": {
    "spanId": "b7ad6b7169821d12",
    "traceId": "b7ad6b7169821d12"
  }
}
```

If you're using **Micrometer Tracing** (with `spring-cloud-starter-sleuth`), `traceId` and `spanId` are automatically
populated in the MDC, and audit events inherit them.

### Parsing and Indexing

**Filebeat** (for Elastic):

```yaml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/audit.log
    fields:
      source: telaio-audit
    json.message_key: message
    json.keys_under_root: true

output.elasticsearch:
  hosts: [ "localhost:9200" ]
  index: "telaio-audit-%{+yyyy.MM.dd}"
```

**Logstash** (for Splunk):

```ruby
input {
  file {
    path => "/var/log/audit.log"
    codec => json
  }
}

output {
  splunk {
    host => "splunk.example.com"
    port => "8088"
    token => "${SPLUNK_HEC_TOKEN}"
    sourcetype => "telaio:audit"
  }
}
```

## Metrics

### Purpose

Metrics provide **quantitative insights** into DAL performance: latency distribution, success/error rates, throughput
per DAL and operation.

### Enabling Metrics

Metrics are **on by default** for every DAL. Exclude a DAL:

```java
@DalService(name = "products")
@DalMetrics(enabled = false)  // No metrics for this DAL
public class ProductDalService extends JpaDal<Product, Long> {
}
```

To measure specific operations only:

```java
@DalService(name = "products")
@DalMetrics(operations = {DalOperationType.READ, DalOperationType.READ_ONE})  // Metrics for reads only
public class ProductDalService extends JpaDal<Product, Long> {
}
```

Or disable globally:

```yaml
telaio:
  metrics:
    enabled: false
```

### Metrics Collection

The `DalMetricsInterceptor` records each operation's:

- **Duration** (latency in milliseconds)
- **Outcome** (SUCCESS, CLIENT_ERROR or ERROR)
- **Bucket** (time window the operation falls into)

A **histogram** buckets latencies into configurable ranges (e.g., 1ms, 2ms, 4ms, 8ms, …). Statistics are computed per
bucket:

- **Count**: How many operations in this bucket
- **Min/Max/Mean**: Latency extremes and average
- **Percentiles**: p50, p90, p95, p99 (configurable)

### Storage Options

#### In-Memory Store (Default)

Zero configuration. Buckets are retained for 24 hours (configurable) with a hard cap of 10,000 buckets. Suitable for
development and small deployments.

```yaml
telaio:
  metrics:
    in-memory:
      retention: 24h
      max-buckets: 10000
```

#### JDBC Store (Persistent)

When a `DataSource` is on the classpath, metrics are automatically persisted to the database. Buckets older than 7
days (configurable) are deleted periodically.

**Supported platforms** (verified on every build):

- PostgreSQL 17+
- MySQL 8.4+
- MariaDB 11.4+
- Oracle 21c+
- SQL Server 2022+
- H2 (test-only)

**Configuration**:

```yaml
telaio:
  metrics:
    jdbc:
      enabled: true
      initialize-schema: always  # Create schema automatically
      platform: postgresql        # Auto-detected if unset
      retention: 7d
      cleanup-interval: 1h
```

When `initialize-schema: always`, Telaio creates the `telaio_metrics_bucket` table automatically. For production, use
`initialize-schema: never` and manage schema externally:

The DDL is shipped per-vendor under
`telaio-metrics/src/main/resources/com/paganbit/telaio/metrics/store/jdbc/schema-<platform>.sql`. The
`@@table_name@@` placeholder is replaced with `telaio.metrics.jdbc.table-name` (default:
`telaio_metrics_bucket`):

```sql
-- schema-postgresql.sql (actual shipped script)
CREATE TABLE IF NOT EXISTS @@table_name@@ (
    bucket_start         TIMESTAMP     NOT NULL,
    bucket_duration_ms   BIGINT        NOT NULL,
    dal_name             VARCHAR(255)  NOT NULL,
    operation            VARCHAR(16)   NOT NULL,
    instance_id          VARCHAR(36)   NOT NULL,
    invocation_count     BIGINT        NOT NULL,
    error_count          BIGINT        NOT NULL,
    client_error_count   BIGINT        NOT NULL,
    total_duration_nanos BIGINT        NOT NULL,
    min_duration_nanos   BIGINT        NOT NULL,
    max_duration_nanos   BIGINT        NOT NULL,
    histogram_counts     VARCHAR(2048) NOT NULL,
    CONSTRAINT @@table_name@@_pk
        PRIMARY KEY (bucket_start, dal_name, operation, instance_id)
);

CREATE INDEX IF NOT EXISTS @@table_name@@_ix1
    ON @@table_name@@ (dal_name, bucket_start);
```

Each row is one completed time bucket per `(bucket_start, dal_name, operation, instance_id)`. Durations
are stored in **nanoseconds** (`total_duration_nanos`, `min_duration_nanos`, `max_duration_nanos`);
there is no outcome column — service errors are counted in `error_count` and client faults
(validation, not-found, optimistic-lock conflicts) in `client_error_count`, alongside the overall
`invocation_count`; the latency histogram is serialized into `histogram_counts`.

> **Schema migration note:** the shipped DDL uses `CREATE TABLE IF NOT EXISTS` and never alters an
> existing table. If you created the metrics table before `client_error_count` existed, add the
> column manually (or drop the table and let the initializer recreate it):
>
> ```sql
> -- PostgreSQL / H2 / MySQL / MariaDB
> ALTER TABLE telaio_metrics_bucket ADD COLUMN client_error_count BIGINT NOT NULL DEFAULT 0;
> -- SQL Server
> ALTER TABLE telaio_metrics_bucket ADD client_error_count BIGINT NOT NULL DEFAULT 0;
> -- Oracle
> ALTER TABLE telaio_metrics_bucket ADD client_error_count NUMBER(19) DEFAULT 0 NOT NULL;
> ```

#### Micrometer Integration (Optional)

For production, you can send metrics to a **Micrometer-backed monitoring system** (Prometheus, Datadog, CloudWatch, New
Relic, etc.) instead of persisting to JDBC:

```yaml
telaio:
  metrics:
    micrometer:
      enabled: true
      metric-name: telaio.dal.operation
```

When enabled, Telaio records each operation into the app's `MeterRegistry`. The backend (configured separately) scrapes
or polls the registry and stores the data.

**In Prometheus** (example):

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: telaio-app
    static_configs:
      - targets: [ 'localhost:8080' ]
    metrics_path: /actuator/prometheus
```

Then query:

```promql
# 95th percentile latency for all operations
histogram_quantile(0.95, rate(telaio_dal_operation_seconds_bucket[5m]))

# Success rate per DAL
rate(telaio_dal_operation_seconds_count{outcome="success"}[5m]) / rate(telaio_dal_operation_seconds_count[5m])

# Client-fault rate (validation, not-found, conflicts) — misbehaving callers, not service health
rate(telaio_dal_operation_seconds_count{outcome="client_error"}[5m])
```

### Actuator Endpoint

The **`/actuator/telaiometrics`** endpoint provides a queryable interface over collected metrics.

**Enable it**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, telaiometrics
```

**Query examples**:

```bash
# Overview (last 1 hour)
curl http://localhost:8080/actuator/telaiometrics

# Response
{
  "from": "2025-07-01T09:30:45Z",
  "to": "2025-07-01T10:30:45Z",
  "dals": [
    {
      "dalName": "products",
      "stats": {
        "count": 42,
        "errorCount": 1,
        "clientErrorCount": 3,
        "meanMs": 145.2,
        "minMs": 3.4,
        "maxMs": 380.5,
        "totalMs": 6098.4,
        "percentilesMs": {
          "p50": 120.0,
          "p90": 210.3,
          "p95": 250.0,
          "p99": 380.5
        }
      }
    }
  ],
  "slowest": [
    {
      "dalName": "products",
      "operation": "CREATE",
      "stats": {
        "count": 42,
        "errorCount": 1,
        "clientErrorCount": 3,
        "meanMs": 145.2,
        "minMs": 3.4,
        "maxMs": 380.5,
        "totalMs": 6098.4,
        "percentilesMs": {
          "p50": 120.0,
          "p90": 210.3,
          "p95": 250.0,
          "p99": 380.5
        }
      }
    }
  ]
}
```

```bash
# Custom date range (ISO 8601), query params: from, to, top
curl "http://localhost:8080/actuator/telaiometrics?from=2025-07-01T00:00:00Z&to=2025-07-02T00:00:00Z"

# Specific DAL (path segment, not a query param)
curl "http://localhost:8080/actuator/telaiometrics/products"

# Specific DAL + operation
curl "http://localhost:8080/actuator/telaiometrics/products/create"

# Top 5 slowest (the config default is telaio.metrics.endpoint.top-n)
curl "http://localhost:8080/actuator/telaiometrics?top=5"
```

## Distributed Tracing

Telaio integrates with **Micrometer Tracing** to correlate operations across services.

If you include `spring-cloud-starter-sleuth` (Spring Boot 3+) or `io.micrometer:micrometer-tracing-bridge-brave` (Spring
Boot 4+), trace IDs are automatically propagated:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<dependency>
<groupId>io.zipkin.reporter2</groupId>
<artifactId>zipkin-sender-urlconnection</artifactId>
</dependency>
```

When enabled, every DAL operation includes the current trace/span IDs, which:

- Appear in audit event's MDC (if `include-mdc=true`)
- Are passed to Micrometer metrics (if `micrometer.enabled=true`)
- Appear in your application's logs (standard Spring Sleuth integration)

**Example Zipkin configuration**:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # Sample 10% of requests
    zipkin:
      endpoint: http://zipkin:9411/api/v2/spans
```

Then trace a request in Zipkin's UI to see all DAL operations and their latencies correlated with your application's
other operations.

## Combining Audit and Metrics

Audit and metrics serve different purposes and can be enabled together:

- **Audit** answers "what happened" with operation details (arguments, principal, outcome reason).
- **Metrics** answers "how fast" with latency distributions and rates.

**Example setup**:

```yaml
telaio:
  audit:
    logging:
      format: JSON
      category: com.paganbit.telaio.audit.AUDIT
  metrics:
    enabled: true
    bucket-duration: 10s
    jdbc:
      enabled: true
      initialize-schema: always

management:
  tracing:
    sampling:
      probability: 0.1
  endpoints:
    web:
      exposure:
        include: health, telaiometrics
```

**Result**:

- Every operation is logged to the audit trail (durable, queryable).
- Latencies are recorded to the metrics store (aggregated for dashboards).
- Traces are sampled and sent to Zipkin (for full distributed tracing).
- The `telaiometrics` endpoint provides a queryable metrics interface.

## Dashboarding Example (Grafana + PostgreSQL)

If using JDBC metrics storage, you can query the metrics table directly from Grafana:

```sql
-- Average latency per DAL per hour (durations are stored in nanoseconds)
SELECT date_trunc('hour', bucket_start)                                       as time,
       dal_name,
       operation,
       SUM(total_duration_nanos) / NULLIF(SUM(invocation_count), 0) / 1000000.0 as avg_latency_ms
FROM telaio_metrics_bucket
WHERE bucket_start > NOW() - INTERVAL '7 days'
GROUP BY 1, 2, 3
ORDER BY time DESC, avg_latency_ms DESC
```

```sql
-- Success rate per DAL (successes = invocation_count - error_count - client_error_count)
SELECT dal_name,
       operation,
       SUM(invocation_count - error_count - client_error_count)::FLOAT / NULLIF(SUM(invocation_count), 0) * 100 as success_rate
FROM telaio_metrics_bucket
WHERE bucket_start > NOW() - INTERVAL '1 day'
GROUP BY dal_name, operation
ORDER BY success_rate ASC
```

## Troubleshooting

**Audit events not appearing**:

- Ensure `@DalAudit` is present on the DAL service.
- Check that the logger category matches your Logback configuration (default: `com.paganbit.telaio.audit.AUDIT`).
- Verify the Logback configuration routes the category to the correct appender.

**Metrics not persisting**:

- Ensure `telaio.metrics.enabled=true` (default).
- Check that a `DataSource` bean is available (Spring Data JPA or similar).
- Verify `telaio.metrics.jdbc.enabled=true` (default).
- Ensure schema is initialized (`initialize-schema: always` or `create` the schema manually).

**Micrometer metrics missing from Prometheus**:

- Ensure `telaio.metrics.micrometer.enabled=true`.
- Check that a `MeterRegistry` bean is available (Spring Boot auto-creates one when Micrometer is on the classpath).
- Verify the metric name in Prometheus (default: `telaio_dal_operation_seconds`).
- Check Prometheus scrape configuration and targets.

**High audit overhead**:

- Enable audit only for sensitive operations: `@DalAudit(operations = {CREATE, UPDATE, DELETE})`.
- Use a dedicated fast appender (e.g., async or batching).
- Consider disabling MDC inclusion if tracing is not needed.

**High metrics memory usage**:

- Increase `bucket-duration` (fewer buckets per hour) or increase `flush-interval` (flush less frequently).
- Reduce `histogram.bucket-count` (fewer buckets per histogram).
- Reduce `in-memory.retention` (evict old buckets sooner) or use JDBC storage.
