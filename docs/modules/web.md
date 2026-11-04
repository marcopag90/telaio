# Telaio: Web Module

The web module exposes DAL operations as REST endpoints. It implements the HTTP binding, request routing, error
handling, and declares the `rest` channel: which operations each DAL exposes there (`@DalWeb`). The adapter chain
that wires together exposure, security, audit and the DAL bean is assembled by `telaio-core`, one per channel.

## Purpose

- **REST API generation:** Dynamic HTTP endpoints from registered DALs
- **Filtering & pagination:** Query parameter support for complex searches
- **Security integration:** Plug-in authorization and RBAC at the boundary
- **Error handling:** RFC 9457 `ProblemDetail` responses with detailed field-level validation errors
- **Per-operation exposure:** Structural control over which operations are published
- **Flexible ID resolution:** Type-safe entity ID extraction via `@DalId`

## Key Public Types

### REST Contract

| Type                     | Purpose                                                                                                                                                                                                                                                                 |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DalRestApiV1`           | Interface defining the HTTP endpoint signatures. Base path: `/dal/v1`. Endpoints: `POST /{dalName}` (201), `GET /{dalName}` (200), `GET /{dalName}/{id}` (200), `PATCH /{dalName}/{id}` (200/204), `DELETE /{dalName}/{id}` (204). Filter param: `q` (Turkraft syntax). |
| `DalRestApiV1Controller` | Implementation (not component-scanned; registered via `@Import`). Routes requests to the appropriate DAL via the `DalOperationAdapterRegistry` assembled by `telaio-core` (see [core](core.md#operation-adapter-assembly)).                                                                                                                          |

### Annotations

| Annotation | Target    | Purpose                                                                                                            |
|------------|-----------|--------------------------------------------------------------------------------------------------------------------|
| `@DalId`   | Parameter | Extracts and converts the entity ID from the path variable (default: `id`). The conversion uses the DAL's ID type. |
| `@DalWeb`  | Class     | Declares the operations a DAL exposes on REST (opt-out: absent = all five, `operations = {…}` = only those, `operations = {}` = not on REST). `@DalService(internal = true)` wins. |

### Request/Response Handling

The operation adapters the controller invokes (`DelegatingDalOperationAdapter`), their registry
(`DalOperationAdapterRegistry`, keyed by channel and DAL name) and the startup assembler
(`DalOperationAdapterAssembler`) live in `telaio-core` since 3.0.0; the controller resolves the `rest` chain
(`DalChannels.REST`). See [Operation adapter assembly](core.md#operation-adapter-assembly).

### The REST Channel

| Type                       | Purpose                                                                                                                                                                                              |
|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DalChannels`              | `DalChannels.REST = DalChannel.of("rest")`, the channel id this module declares.                                                                                                                     |
| `DalRestChannelDefinition` | The `DalChannelDefinition` bean of the REST channel: reads `@DalWeb` (through the class hierarchy) and answers which operations a DAL exposes. Core builds the `rest` chains from it and enforces the answer. |
| `DalRestOperationMapping`  | The one mapping of operations onto the REST surface: URI templates, collection (`CREATE`, `READ`) vs item (`READ_ONE`, `UPDATE`, `DELETE`) groups, HTTP methods, and the `Allow` computation. Shared by the contract, the exception handler and `telaio-openapi`. |

### Error Handling

| Type                                 | Purpose                                                                                                                                                                                                                                                                                       |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TelaioWebExceptionHandler`          | Exception handler scoped to `DalRestApiV1Controller`: maps `DalEntityValidationException` (400), Turkraft's `InvalidSyntaxException` (400 `"Malformed filter expression"`), `DalInvalidFilterException` (400 `"Invalid filter expression"`, including the RBAC subclass `DalFilterFieldNotReadableException`), `DalInvalidSortException` (400 `"Invalid sort parameter"`, including `DalSortFieldNotReadableException`), `DalIdCodecException` (400 `"Malformed resource identifier"`), `DalEntityNotFoundException` / `DalNotFoundException` (404), `OptimisticLockingFailureException` (409, generic detail), `DalRegistryException` (500, generic detail) to RFC 9457 `ProblemDetail`; and core's `DalOperationNotExposedException` to 405 with an `Allow` header computed from the DAL's `@DalWeb` through `DalRestOperationMapping`, or 404 (empty body) when the URI serves nothing. |
| `TelaioAccessDeniedExceptionHandler` | Exception handler scoped to `DalRestApiV1Controller`: maps Spring Security's `AccessDeniedException` (including `DalAccessDeniedException`) to 403 `ProblemDetail` with minimal detail (OWASP CWE-209). Registered only when Spring Security is present. A host's own controllers keep Spring Security's default behavior.                                                                  |
| `ValidationError`                    | Detail object in validation error responses: `object`, `field`, `rejectValue`, `message`                                                                                                                                                                                                      |

### Configuration

| Type                         | Purpose                                        |
|------------------------------|------------------------------------------------|
| `TelaioWebProperties`        | Configuration properties (prefix `telaio.web`) |
| `TelaioWebAutoConfiguration` | Spring Boot autoconfiguration                  |

## How Developers Use It

### The REST API Is Generated Automatically

Once you declare a DAL service, the REST API exists with no additional controller code:

```java

@DalService(name = "announcements")
public class AnnouncementDalService extends JpaDal<Announcement, Long> {
}
```

→ Endpoints appear at:

- `POST /dal/v1/announcements` → Create (201)
- `GET /dal/v1/announcements` → List with filter/pagination (200)
- `GET /dal/v1/announcements/{id}` → Read one (200)
- `PATCH /dal/v1/announcements/{id}` → Update (200/204)
- `DELETE /dal/v1/announcements/{id}` → Delete (204)

### Use Filtering and Pagination

The `q` parameter accepts Turkraft Spring Filter syntax:

```bash
# Simple equality
curl 'http://localhost:8080/dal/v1/announcements?q=type:%27URGENT%27'

# Compound condition
curl 'http://localhost:8080/dal/v1/announcements?q=type:%27URGENT%27%20and%20createdAt%3e%272026-01-01%27'

# Pagination
curl 'http://localhost:8080/dal/v1/announcements?page=0&size=20&sort=createdAt,desc'
```

Details: see [REST API Guide](../rest-api.md).

### Control Which Operations Are Exposed

```java

@DalService(name = "articles")
@DalWeb(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
public class ArticleDalService extends JpaDal<Article, Long> {
}
```

→ Create/update/delete requests get:

- 405 Method Not Allowed (with an `Allow` header listing only the exposed sibling methods — here `Allow: GET`) if the
  collection/item URI is otherwise active
- 404 Not Found (empty body) if the URI exposes no operation

`@DalWeb` absent means every operation; `@DalWeb(operations = {})` takes the DAL off REST entirely (same 404 as an
unknown name, not documented in OpenAPI). The rule is enforced by core's exposure interceptor; this module declares
it (`DalRestChannelDefinition`) and translates the rejection.

### Hide a DAL Entirely

```java

@DalService(name = "app-settings", internal = true)
public class AppSettingDalService extends JpaDal<AppSetting, String> {
}
```

→ No REST endpoint; in-process code can still use `DalRegistry.getServiceByName("app-settings")`.

### Resolve Entity IDs with Type Safety

When a DAL uses a non-numeric ID type, use `@DalId` to extract and convert:

```java

@DalService(name = "settings")
public class SettingDalService extends JpaDal<Setting, String> {
}
```

The contract method (from `DalRestApiV1`) declares the id as `Object`; `DalIdArgumentResolver` converts the raw path
segment to the target DAL's actual id type:

```java
Object readOne(
    @PathVariable String dalName,
    @DalId Object id
);
```

→ `GET /dal/v1/settings/MY_SETTING_KEY` resolves `id` as `"MY_SETTING_KEY"` (String, not Long), because the target
DAL's id type is `String`.

**Composite IDs:** when the DAL's id class is a complex type (e.g. a JPA `@EmbeddedId`), the `{id}` path segment
must be the id's JSON representation encoded as **Base64 URL-safe**; `DalIdArgumentResolver` decodes it and
deserializes the JSON into the id class (in request/response bodies the id stays a plain nested JSON object):

```text
GET /dal/v1/translations/eyJtZXNzYWdlS2V5IjoiZ3JlZXRpbmciLCJsb2NhbGUiOiJpdCJ9
                          └── base64url({"messageKey":"greeting","locale":"it"}) ──┘
```

See [REST API — Composite IDs](../rest-api.md#composite-ids) for the full client-side contract and examples.

### Handle Errors

All errors are returned as RFC 9457 `ProblemDetail` (content-type `application/problem+json`):

**Validation error (400):**

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/dal/v1/products",
  "errors": [
    {
      "object": "product",
      "field": "price",
      "rejectValue": -1,
      "message": "must be greater than 0"
    },
    {
      "object": "product",
      "field": "name",
      "rejectValue": null,
      "message": "must not be blank"
    }
  ]
}
```

**Not found (404):**

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Product was not found for id: [999]",
  "instance": "/dal/v1/products/999"
}
```

**Access denied (403):**

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "instance": "/dal/v1/products/1"
}
```

(The reason is logged, not exposed, per OWASP.)

**Operation not exposed (405 or 404):**

```
405 Method Not Allowed
Allow: GET
```

(The `Allow` header lists only the exposed sibling methods on that URI; the body is intentionally empty.)

## Configuration

### Properties (prefix `telaio.web`)

| Property          | Type    | Default | Purpose                                                                                                                                                                                                                                          |
|-------------------|---------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `openapi.enabled` | Boolean | `true`  | Whether to register the branded `TELAIO` OpenAPI group. On by default; enabling it declares a `GroupedOpenApi`, which switches springdoc into grouped mode, so the consuming app must then declare its own groups to keep its endpoints visible. |

## See Also

- [REST API Guide](../rest-api.md) — Detailed endpoint reference and filtering syntax
- [Security Module](./security.md) — Authorization and RBAC at the boundary
- [Configuration Reference](../configuration.md) — All web properties
- [Architecture](../architecture.md) — The adapter chain and error handling
- [Getting Started](../getting-started.md) — Your first REST API
