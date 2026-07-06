# Telaio: Audit Module

The audit module records a durable trail of DAL operations: who performed what, when, with what arguments, and what the
outcome was. Auditing is **opt-in** via `@DalAudit`.

## Purpose

- **Operation logging:** Records create/read/update/delete events
- **Success/error tracking:** Distinguishes successful, failed, and denied attempts
- **Flexible output:** TEXT (logfmt) or JSON Lines for log aggregation
- **Correlation:** Optional MDC (trace ID, span ID) embedding
- **Extensible:** Pluggable event store and formatter SPIs

## Key Public Types

### Annotations

| Annotation  | Target | Purpose                                                                                                                                   |
|-------------|--------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `@DalAudit` | Class  | Enables auditing for a DAL. Attribute: `operations()` (default empty = audit all operations). Omitting this annotation disables auditing. |

### Events & Storage

| Type                        | Purpose                                                                                                     |
|-----------------------------|-------------------------------------------------------------------------------------------------------------|
| `DalAuditEvent`             | Immutable snapshot: timestamp, DAL name, operation, outcome, principal, arguments, duration, optional error |
| `DalAuditEventStore`        | SPI: `store(DalAuditEvent)` → void                                                                          |
| `LoggingDalAuditEventStore` | Built-in: writes events to the configured logger category                                                   |

### Formatting

| Type                           | Purpose                                                            |
|--------------------------------|--------------------------------------------------------------------|
| `DalAuditEventFormatter`       | SPI: `format(DalAuditEvent)` → `String` (single-line, thread-safe) |
| `LogfmtDalAuditEventFormatter` | Built-in: `key=value` format for human reading (TEXT)              |
| `JsonDalAuditEventFormatter`   | Built-in: JSON Lines format for machine ingestion (JSON)           |

### Support

| Type                          | Purpose                                                                                                                                                                                                                                    |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DalAuditOutcome`             | Enum: `SUCCESS`, `VALIDATION`, `NOT_FOUND`, `CONFLICT`, `ERROR`, `DENIED`                                                                                                                                                                                                         |
| `DalAuditOutcomeClassifier`   | SPI: maps exceptions to outcomes. With Spring Security on the classpath, `SecurityDalAuditOutcomeClassifier` maps `AccessDeniedException` → DENIED and everything else via the shared `DalFailureKind` taxonomy (VALIDATION / NOT_FOUND / CONFLICT / ERROR); without it, `DefaultDalAuditOutcomeClassifier` applies the same taxonomy minus DENIED |
| `DalAuditPrincipalResolver`   | SPI: `resolvePrincipal()` — resolves the current principal name from context (no arguments)                                                                                                                                                |
| `DalAuditArgumentSnapshotter` | Internal: safely copies operation arguments without triggering lazy loading                                                                                                                                                                |

## How Developers Use It

### Enable Auditing on a DAL

```java
@DalService(name = "articles")
@DalAudit
public class ArticleDalService extends JpaDal<Article, Long> {
}
```

Every operation (create, read, readOne, update, delete) is now audited.

### Audit Only Specific Operations

```java
@DalService(name = "articles")
@DalAudit(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
public class ArticleDalService extends JpaDal<Article, Long> {
}
```

Only read operations are recorded; writes are not.

### Example Audit Event

When `POST /dal/v1/articles { "title": "Hello" }` is called by user `alice`:

**TEXT format (default):**

```
timestamp=2026-07-02T10:15:30.123Z dal=articles operation=CREATE outcome=SUCCESS principal=alice durationMs=45 arg.input="{title=Hello}" mdc.spanId=def456 mdc.traceId=abc123
```

**JSON format:**

```json
{
  "timestamp": "2026-07-02T10:15:30.123Z",
  "event": "dal.audit",
  "dal": "articles",
  "operation": "CREATE",
  "outcome": "SUCCESS",
  "principal": "alice",
  "durationMs": 45,
  "args": {
    "input": "{title=Hello}"
  },
  "mdc": {
    "spanId": "def456",
    "traceId": "abc123"
  }
}
```

## Configuration

### Properties (prefix `telaio.audit`)

| Property              | Type       | Default                          | Purpose                                                                                                                       |
|-----------------------|------------|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `logging.format`      | TEXT, JSON | TEXT                             | Serialization format: `TEXT` (logfmt, human) or `JSON` (JSON Lines, machine)                                                  |
| `logging.category`    | String     | `com.paganbit.telaio.audit.AUDIT` | Logger category for events. Configure a dedicated appender in `logback-spring.xml` to route auditing to a separate file/index |
| `logging.include-mdc` | Boolean    | `true`                           | Whether to copy the current MDC (trace ID, span ID) onto each event                                                           |

### Example logback-spring.xml

Route audit events to a separate file:

```xml
<appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/audit.log</file>
    <encoder>
        <pattern>%msg%n</pattern>
    </encoder>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>logs/audit.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
        <maxFileSize>100MB</maxFileSize>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
</appender>

<logger name="com.paganbit.telaio.audit.AUDIT" level="INFO" additivity="false">
<appender-ref ref="AUDIT_FILE"/>
</logger>
```

With `format=JSON`, use a plain `%msg%n` encoder (the event is already JSON).

## See Also

- [Observability Guide](../observability.md) — Audit + Metrics together
- [Configuration Reference](../configuration.md) — All audit properties
- [Architecture](../architecture.md) — Audit's place in interception
- [Core Module](./core.md) — The DAL contract
