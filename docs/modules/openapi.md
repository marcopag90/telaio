# Telaio: OpenAPI Module

The OpenAPI module auto-generates concrete, per-DAL OpenAPI documentation. It replaces the opaque templated operations
that springdoc would otherwise derive from the generic `DalRestApiV1Controller`.

## Purpose

- **Per-DAL documentation:** Each registered DAL gets its own concrete endpoint descriptions
- **Operation control:** Respects `@DalService(internal)` and `@DalService(operations)` to omit non-exposed endpoints
- **Schema generation:** Introspects entity types to document request/response schemas
- **Filter documentation:** Documents the `q` parameter with Turkraft filter syntax
- **Field visibility:** Derives `readOnly`/`writeOnly` from Jackson `@JsonProperty` access annotations

## Key Public Types

### Customization

| Type                   | Purpose                                                                                                                                                                           |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DalOpenApiCustomizer` | Implements `TelaioOpenApiGroupCustomizer` (the SPI from telaio-web). Enriches only the branded `TELAIO` group with concrete per-DAL paths (never the consuming app's own groups). |

### Generation

| Type                       | Purpose                                                                                                                  |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `DalPathsGenerator`        | Synthesizes the OpenAPI path set for all exposed DALs: generates one path item per DAL with only the exposed operations. |
| `DalEntitySchemaResolver`  | Introspects entity types to generate JSON schemas, honoring `@JsonView` and Jackson access rules.                        |
| `FilterParameterDescriber` | Documents the `q` filter parameter with Turkraft syntax and examples.                                                    |

### Configuration

| Type                             | Purpose                                            |
|----------------------------------|----------------------------------------------------|
| `TelaioOpenApiProperties`        | Configuration properties (prefix `telaio.openapi`) |
| `TelaioOpenApiAutoConfiguration` | Spring Boot autoconfiguration                      |

## How Developers Use It

### OpenAPI Documentation Is Generated Automatically

Once you declare DAL services with the web module, OpenAPI documentation is generated in the `TELAIO` group:

```java
@DalService(name = "announcements")
public class AnnouncementDalService extends JpaDal<Announcement, Long> {
}
```

→ Swagger UI shows all five CRUD operations with concrete schemas.

### Internal DALs and Operation Exposure Are Respected

```java
@DalService(name = "app-settings", internal = true)
public class AppSettingDalService extends JpaDal<AppSetting, String> {
}
```

→ `app-settings` is **not** documented in OpenAPI (it is internal).

```java
@DalService(name = "articles", operations = {DalOperationType.READ, DalOperationType.READ_ONE})
public class ArticleDalService extends JpaDal<Article, Long> {
}
```

→ OpenAPI shows **only** the two read operations; create/update/delete are omitted.

### Field Visibility

The schema for each entity respects Jackson access rules:

```java
@Entity
public class Product {
    @Id
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal marginPercentage;  // Visible in responses, not in requests

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;  // Visible in requests, not in responses

    private String name;  // Visible in both
}
```

The generated schema marks `marginPercentage` as `readOnly: true` and `password` as `writeOnly: true`.

### Schema Example

For the `Announcement` entity, Swagger UI displays:

```
POST /dal/v1/announcements
  Request body: { type, title, message, ... }
  Response (201): { id, type, title, message, ... }

GET /dal/v1/announcements
  Query parameters: q (filter), page, size, sort
  Response (200): { content: [...], totalElements, ... }

GET /dal/v1/announcements/{id}
  Response (200): { id, type, title, message, ... }

PATCH /dal/v1/announcements/{id}
  Request body: { type?, title?, message?, ... }  (partial update)
  Response (200/204): { id, type, title, message, ... }

DELETE /dal/v1/announcements/{id}
  Response (204): (no content)
```

## Configuration

### Properties (prefix `telaio.openapi`)

| Property           | Type    | Default                                 | Purpose                                                            |
|--------------------|---------|-----------------------------------------|--------------------------------------------------------------------|
| `enabled`          | Boolean | `true` (when telaio-openapi is present) | Whether the branded `TELAIO` OpenAPI group is registered           |
| `include-examples` | Boolean | `false`                                 | Whether to include example values in operation descriptions        |
| `tag-per-dal`      | Boolean | `true`                                  | Whether each DAL gets its own OpenAPI tag (grouping in Swagger UI) |

### Example application.yaml

```yaml
telaio:
  openapi:
    enabled: true
    include-examples: true
    tag-per-dal: false
```

## See Also

- [REST API Guide](../rest-api.md) — The HTTP endpoint contract
- [Web Module](./web.md) — Generates the REST endpoints
- [Configuration Reference](../configuration.md) — All OpenAPI properties
- [Architecture](../architecture.md) — Relationship to the REST layer
