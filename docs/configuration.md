# Configuration Reference

This guide documents all `telaio.*` configuration properties and their defaults.

## Overview

Telaio configuration uses Spring Boot's `@ConfigurationProperties` pattern. All properties are optional and have
sensible defaults suitable for both development and production.

## Web Configuration

**Prefix**: `telaio.web`

| Property          | Type    | Default | Description                                                                                                                                                                                             |
|-------------------|---------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `openapi.enabled` | boolean | `true`  | Whether to register the branded `TELAIO` OpenAPI group with springdoc. If your app uses `GroupedOpenApi` elsewhere, you may want to set this to `false` to avoid switching springdoc into grouped mode. |

**Example**:

```yaml
telaio:
  web:
    openapi:
      enabled: true
```

## OpenAPI Configuration

**Prefix**: `telaio.openapi`

| Property           | Type    | Default | Description                                                                                                                                                                                                       |
|--------------------|---------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled`          | boolean | `true`  | Whether per-DAL OpenAPI operations are auto-generated. When enabled, contributes an `OpenApiCustomizer` (not a `GroupedOpenApi`), so it enriches the existing spec without switching springdoc into grouped mode. |
| `include-examples` | boolean | `false` | Whether to attach a ready-to-paste example filter expression to the generated `q` filter parameter, derived from each entity's fields.                                                                            |
| `tag-per-dal`      | boolean | `true`  | Whether each DAL's operations are tagged with the DAL name, so Swagger UI groups them per DAL. When `false`, all operations share a single `DAL` tag.                                                             |

**Example**:

```yaml
telaio:
  openapi:
    enabled: true
    include-examples: false
    tag-per-dal: true
```

## Audit Configuration

**Prefix**: `telaio.audit`

| Property              | Type                    | Default                          | Description                                                                                                                                                                       |
|-----------------------|-------------------------|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `logging.format`      | enum (`TEXT` or `JSON`) | `TEXT`                           | Serialization format for audit events. `TEXT` emits human-readable logfmt `key=value` lines; `JSON` emits one JSON object per line (JSON Lines) for ingestion by log aggregators. |
| `logging.category`    | string                  | `com.paganbit.telaio.audit.AUDIT` | Logger category under which audit events are emitted. Use a dedicated category to route audit logs to a separate appender/file/index.                                             |
| `logging.include-mdc` | boolean                 | `true`                           | Whether to copy the current MDC (e.g., `traceId`, `spanId` from Micrometer Tracing) onto each audit event for correlation.                                                        |

**Example**:

```yaml
telaio:
  audit:
    logging:
      format: JSON           # or TEXT
      category: com.paganbit.telaio.audit.AUDIT
      include-mdc: true
```

### Logback Configuration for Audit JSON

If using JSON audit format, configure Logback to write clean JSON Lines without wrapping:

```xml
<appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/audit.log</file>
    <encoder>
        <pattern>%msg%n</pattern>  <!-- Plain message, no timestamp/level/logger -->
    </encoder>
</appender>

<logger name="com.paganbit.telaio.audit.AUDIT" level="INFO" additivity="false">
<appender-ref ref="AUDIT_FILE"/>
</logger>
```

This ensures each audit event is emitted as a single JSON object per line, suitable for Filebeat or other log
forwarders.

## Metrics Configuration

**Prefix**: `telaio.metrics`

### Core Settings

| Property          | Type     | Default | Description                                                                                                                                                                                                    |
|-------------------|----------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled`         | boolean  | `true`  | Whether DAL metrics collection is enabled globally. When `false`, all metrics recording is disabled. Individual DALs can be excluded with `@DalMetrics(enabled=false)`.                                        |
| `bucket-duration` | duration | `1m`    | Length of one aggregation window. Smaller buckets (e.g., `10s`) provide finer trends; larger buckets (e.g., `5m`) reduce storage overhead. Must match across instances for correct multi-instance aggregation. |
| `flush-interval`  | duration | `1m`    | How often completed buckets are flushed from the in-memory aggregator to the stores.                                                                                                                           |

### Histogram Settings

| Property                      | Type            | Default                  | Description                                                                                        |
|-------------------------------|-----------------|--------------------------|----------------------------------------------------------------------------------------------------|
| `histogram.first-upper-bound` | duration        | `1ms`                    | Upper bound of the first latency histogram bucket. Buckets grow geometrically.                     |
| `histogram.growth-factor`     | number          | `2.0`                    | Geometric growth factor between consecutive bucket bounds (e.g., `2.0` = each bucket is 2× wider). |
| `histogram.bucket-count`      | integer         | `20`                     | Total number of histogram buckets, including the overflow bucket.                                  |
| `histogram.quantiles`         | list of numbers | `[0.5, 0.9, 0.95, 0.99]` | Quantiles (percentiles) computed from histograms (e.g., `0.99` = 99th percentile latency).         |

**Warning**: Changing histogram settings makes previously persisted histograms incomparable. Old buckets still
contribute to count/min/max/mean, but percentiles are skipped.

### In-Memory Store Settings

| Property                | Type     | Default | Description                                                                                         |
|-------------------------|----------|---------|-----------------------------------------------------------------------------------------------------|
| `in-memory.retention`   | duration | `24h`   | How long buckets are retained in memory (the fallback when no JDBC store is available).             |
| `in-memory.max-buckets` | integer  | `10000` | Hard cap on retained in-memory buckets; oldest are evicted first. Prevents unbounded memory growth. |

### JDBC Store Settings

| Property                 | Type                                 | Default                 | Description                                                                                                                                                                                                             |
|--------------------------|--------------------------------------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `jdbc.enabled`           | boolean                              | `true`                  | Whether the JDBC store is used when a `DataSource` is available.                                                                                                                                                        |
| `jdbc.table-name`        | string                               | `telaio_metrics_bucket` | Name of the metrics table (plain, unqualified SQL identifier).                                                                                                                                                          |
| `jdbc.initialize-schema` | enum (`EMBEDDED`, `ALWAYS`, `NEVER`) | `EMBEDDED`              | Schema initialization mode. `EMBEDDED` = H2/Derby only; `ALWAYS` = any database; `NEVER` = application manages schema.                                                                                                  |
| `jdbc.retention`         | duration                             | `7d`                    | How long persisted buckets are retained; older rows are deleted periodically.                                                                                                                                           |
| `jdbc.cleanup-interval`  | duration                             | `1h`                    | How often expired rows are deleted. Cleanup is throttled per instance to avoid lock contention.                                                                                                                         |
| `jdbc.platform`          | string                               | (auto-detected)         | Database platform for schema script resolution (e.g., `postgresql`, `mysql`, `oracle`). Auto-detected from the `DataSource` when unset. Supported: PostgreSQL 17, MySQL 8.4, MariaDB 11.4, Oracle 21c, SQL Server 2022. |

### Micrometer Integration

| Property                 | Type    | Default                | Description                                                                                                                                                                                                                          |
|--------------------------|---------|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `micrometer.enabled`     | boolean | `false`                | Whether DAL timings are recorded into a Micrometer `MeterRegistry`. Opt-in (not keyed off classpath presence). When enabled, supersedes the in-house path and sends metrics to your backend (Prometheus, CloudWatch, Datadog, etc.). |
| `micrometer.metric-name` | string  | `telaio.dal.operation` | Name of the Micrometer `Timer`, tagged with `dal`, `operation`, and `outcome`.                                                                                                                                                       |

### In-House Path Control

| Property           | Type    | Default          | Description                                                                                                                                                                                |
|--------------------|---------|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `in-house.enabled` | boolean | (null = dynamic) | Control the in-house metrics path (aggregator, flusher, `telaiometrics` endpoint). `null` (unset) = "on unless Micrometer takes over"; `true` = always on (run both paths); `false` = off. |

### Actuator Endpoint

| Property                 | Type     | Default | Description                                                              |
|--------------------------|----------|---------|--------------------------------------------------------------------------|
| `endpoint.default-range` | duration | `1h`    | Time range queried when a client doesn't specify `from`/`to` parameters. |
| `endpoint.top-n`         | integer  | `10`    | Size of the slowest-operations ranking in the endpoint overview.         |

**Example**:

```yaml
telaio:
  metrics:
    enabled: true
    bucket-duration: 10s        # Finer granularity for live systems
    flush-interval: 10s
    histogram:
      first-upper-bound: 1ms
      growth-factor: 2.0
      bucket-count: 20
      quantiles: [ 0.5, 0.9, 0.95, 0.99 ]
    in-memory:
      retention: 24h
      max-buckets: 10000
    jdbc:
      enabled: true
      table-name: telaio_metrics_bucket
      initialize-schema: always  # For non-embedded databases
      retention: 7d
      cleanup-interval: 1h
      platform: postgresql        # Or mysql, mariadb, oracle, sqlserver (h2 is test-only)
    micrometer:
      enabled: false              # Set to true to use Micrometer instead
      metric-name: telaio.dal.operation
    endpoint:
      default-range: 1h
      top-n: 10
```

## Accessing the Metrics Endpoint

The actuator endpoint is registered at `/actuator/telaiometrics` when metrics are enabled.

**Expose it in your application properties**:

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

# Query a date range (ISO 8601 format)
curl "http://localhost:8080/actuator/telaiometrics?from=2025-07-01T00:00:00Z&to=2025-07-02T00:00:00Z"

# Get top 5 slowest operations
curl "http://localhost:8080/actuator/telaiometrics?top=5"
```

## Security Configuration

**Prefix**: `telaio.security`

Telaio does not define top-level security properties. Security is controlled via:

- `@DalSecurity(authAdapterClass = …, rbacAdapterClass = …)` on DAL services
- Your Spring Security configuration
- Custom `DalAuthAdapter` and `DalRbacAdapter` implementations

See [Security Guide](security-guide.md) for details.

## Profile-Specific Example

**`application-dev.yaml`** (development):

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # Fresh schema each startup
    show_sql: true

telaio:
  audit:
    logging:
      format: TEXT           # Human-readable for logs
  metrics:
    enabled: true
    bucket-duration: 10s
    jdbc:
      enabled: false         # Use in-memory store
  openapi:
    enabled: true
    include-examples: true   # Show filter examples
```

**`application-prod.yaml`** (production):

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate    # Schema is managed externally

telaio:
  audit:
    logging:
      format: JSON          # For log aggregation
      category: com.paganbit.telaio.audit.AUDIT
  metrics:
    enabled: true
    bucket-duration: 1m
    flush-interval: 1m
    jdbc:
      enabled: true
      initialize-schema: never  # Schema already exists
      retention: 30d
      platform: postgresql
      cleanup-interval: 6h
    micrometer:
      enabled: true              # Send to Prometheus/Datadog/etc
  openapi:
    enabled: false              # Optional in production

management:
  endpoints:
    web:
      exposure:
        include: health, telaiometrics
```

## Validation

Telaio validates configuration properties when a Bean Validation provider is on the classpath (e.g., Hibernate
Validator). If constraints are violated, startup fails with a clear error message.

Common violations:

- `bucket-duration` or `flush-interval` not set (must be a valid `Duration`)
- `histogram.growth-factor` ≤ 1.0 (must be > 1.0)
- `histogram.bucket-count` < 2 (must be ≥ 2)
- `histogram.quantiles` outside (0.0, 1.0] range

## Disabling Features

To disable a feature entirely:

```yaml
# Disable all metrics
telaio:
  metrics:
    enabled: false

# Disable JDBC metrics store (use in-memory only)
telaio:
  metrics:
    jdbc:
      enabled: false

# Disable OpenAPI auto-generation
telaio:
  openapi:
    enabled: false

# Disable OpenAPI group (if using grouped mode)
telaio:
  web:
    openapi:
      enabled: false
```

## Environment Variables

Spring Boot's property resolution includes environment variables. To set a property via env var, use the `TELAIO_*`
naming convention:

```bash
export TELAIO_METRICS_ENABLED=false
export TELAIO_AUDIT_LOGGING_FORMAT=JSON
export TELAIO_OPENAPI_INCLUDE_EXAMPLES=true
```

The mapping is automatic: `telaio.audit.logging.format` → `TELAIO_AUDIT_LOGGING_FORMAT`.

## Secrets Management

For sensitive values (database credentials, OAuth tokens), use Spring's secret management tools:

- **Spring Cloud Config**: Centralized configuration server
- **HashiCorp Vault**: Dynamic secrets
- **AWS Secrets Manager**, **Azure Key Vault**: Cloud-native secret stores
- **`.env` files with `@ConfigurationProperties` decryption**: Local development

Telaio does not define secrets; you manage them in Spring Security and your `DalAuthAdapter` implementations.
