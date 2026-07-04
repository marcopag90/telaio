# REST API Reference

This guide documents the generated REST API surface, request/response formats, filtering, and error codes.

## Base Path and Naming

All DAL endpoints are rooted at `/dal/v1/{dalName}`, where `{dalName}` is the name you provide in
`@DalService(name = "…")`.

Example: `@DalService(name = "products")` exposes endpoints under `/dal/v1/products`.

## Endpoints

### Create

**Request:**

```http
POST /dal/v1/{dalName}
Content-Type: application/json

{
  "field1": "value1",
  "field2": 123,
  "nested": { "subfield": "..." }
}
```

**Response (201 Created):**

```json
{
  "id": 1,
  "field1": "value1",
  "field2": 123,
  "nested": {
    "subfield": "..."
  },
  "createdAt": "2025-07-01T10:30:00Z"
}
```

**Status Codes:**

- **201 Created** — Entity saved successfully. Response body contains the created entity with generated `id`.
- **400 Bad Request** — Validation failed or malformed input. Response is `ProblemDetail` with field-level `errors`
  extension.
- **403 Forbidden** — Principal not authorized to create. Response is `ProblemDetail` with generic title/no detail.
- **404 Not Found** — DAL service not found, or the URI exposes no operation at all (an internal DAL answers 404 as "DAL service not found").
- **405 Method Not Allowed** — CREATE is not exposed but the URI still exposes another method (`Allow` header lists
  them).
- **500 Internal Server Error** — Unexpected server error. Logged but never leaked to the client.

### Read (List with Pagination and Filter)

**Request:**

```http
GET /dal/v1/{dalName}?q=filter&page=0&size=20&sort=name,desc
```

**Query Parameters:**

| Parameter | Type    | Default          | Description                                                                                    |
|-----------|---------|------------------|------------------------------------------------------------------------------------------------|
| `q`       | string  | (none)           | Turkraft Spring Filter expression (see [Filtering](#filtering-query-language) below)           |
| `page`    | integer | `0`              | Zero-indexed page number                                                                       |
| `size`    | integer | `20`             | Number of results per page                                                                     |
| `sort`    | string  | (entity default) | Comma-separated sort fields with optional `,asc` or `,desc` (e.g., `name,asc` or `price,desc`) |

**Response (200 OK):**

```json
{
  "content": [
    {
      "id": 1,
      "name": "Laptop",
      "price": 999.99,
      ...
    },
    {
      "id": 2,
      "name": "Mouse",
      "price": 29.99,
      ...
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

**Status Codes:**

- **200 OK** — Page of entities returned (may be empty if no matches).
- **403 Forbidden** — Principal not authorized to read.
- **404 Not Found** — DAL service not found, or the URI exposes no operation at all (an internal DAL answers 404 as "DAL service not found").
- **405 Method Not Allowed** — READ is not exposed but the URI still exposes another method (`Allow` header lists
  them).
- **400 Bad Request** — Malformed filter or pagination parameters.

**Examples:**

```bash
# All products, paginated
curl "http://localhost:8080/dal/v1/products?page=0&size=10"

# Filter: category is "electronics"
curl "http://localhost:8080/dal/v1/products?q=category:'electronics'"

# Filter with sort
curl "http://localhost:8080/dal/v1/products?q=category:'electronics'&sort=price,desc&page=0&size=5"

# Complex filter: category AND price
curl "http://localhost:8080/dal/v1/products?q=category:'electronics'%20and%20price>500"
```

### Read One

**Request:**

```http
GET /dal/v1/{dalName}/{id}
```

**Path Parameters:**

| Parameter | Type   | Description                                                                                                                                                    |
|-----------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`      | varies | The entity ID. Type depends on the entity's `@Id` field (e.g., `Long`, `String`, UUID). Composite keys are Base64-encoded JSON — see [Composite IDs](#composite-ids). |

**Response (200 OK):**

```json
{
  "id": 1,
  "name": "Laptop",
  "price": 999.99,
  "description": "High-performance laptop",
  "available": true
}
```

**Status Codes:**

- **200 OK** — Entity found and returned.
- **403 Forbidden** — Principal not authorized to read.
- **404 Not Found** — DAL service not found, the URI exposes no operation at all, or the entity with the given `id`
  doesn't exist.
- **405 Method Not Allowed** — READ_ONE is not exposed but the URI still exposes another method (`Allow` header lists
  them).

**Example:**

```bash
curl http://localhost:8080/dal/v1/products/1
```

### Update (Partial PATCH)

**Request:**

```http
PATCH /dal/v1/{dalName}/{id}
Content-Type: application/json

{
  "price": 1299.99
}
```

The PATCH body uses **RFC 7396 merge**: only the provided fields are updated. The entity is fetched, merged with the
patch object, validated, and saved.

**Response (200 OK):**

- **200 OK**: Response includes the updated entity (same as Read One). This is the normal outcome:
  `AbstractDal.update` re-reads the entity after saving, so a successful PATCH returns the entity.
- **204 No Content**: A contract edge case only — returned if the update yields an empty `Optional`, which does not
  happen on the normal path.

**Status Codes:**

- **200 OK** — Entity updated successfully (the normal outcome; the updated entity is returned).
- **400 Bad Request** — Validation failed after merge. Response is `ProblemDetail` with field-level `errors`.
- **403 Forbidden** — Principal not authorized to update.
- **404 Not Found** — DAL service not found, the URI exposes no operation at all, or the entity doesn't exist.
- **405 Method Not Allowed** — UPDATE is not exposed but the URI still exposes another method (`Allow` header lists
  them).
- **500 Internal Server Error** — Unexpected error during merge or save.

**Examples:**

```bash
# Update price only
curl -X PATCH http://localhost:8080/dal/v1/products/1 \
  -H "Content-Type: application/json" \
  -d '{"price": 1299.99}'

# Update multiple fields
curl -X PATCH http://localhost:8080/dal/v1/products/1 \
  -H "Content-Type: application/json" \
  -d '{"price": 1299.99, "available": false}'
```

### Delete

**Request:**

```http
DELETE /dal/v1/{dalName}/{id}
```

**Response (204 No Content):**

No response body is returned on successful deletion.

**Status Codes:**

- **204 No Content** — Entity deleted successfully.
- **403 Forbidden** — Principal not authorized to delete.
- **404 Not Found** — DAL service not found, the URI exposes no operation at all, or the entity doesn't exist.
- **405 Method Not Allowed** — DELETE is not exposed but the URI still exposes another method (`Allow` header lists
  them).

**Example:**

```bash
curl -X DELETE http://localhost:8080/dal/v1/products/1
```

### Composite IDs

When an entity uses a composite key (JPA `@EmbeddedId` or `@IdClass` — i.e. the DAL's `getIdClass()` is a
complex type rather than `Long`, `String`, UUID, …), the ID crosses the wire in two different shapes:

- **In request/response bodies** (Create, Read, Update responses) the ID is a plain **nested JSON object**.
- **In the `{id}` path segment** (Read One, Update, Delete) it is the ID's JSON representation encoded as
  **Base64 URL-safe** (RFC 4648 §5; padding optional). `DalIdArgumentResolver` decodes the segment and
  deserializes it into the DAL's ID class.

Simple IDs are unaffected: they are passed as-is in the path (`/dal/v1/products/1`).

**Example** — the showcase `translations` DAL uses the composite key `TranslationId { messageKey, locale }`:

```text
JSON id:      {"messageKey":"greeting","locale":"it"}
Path segment: eyJtZXNzYWdlS2V5IjoiZ3JlZXRpbmciLCJsb2NhbGUiOiJpdCJ9
```

```bash
# Encode the id (Base64 URL-safe, padding stripped)
ID=$(printf '%s' '{"messageKey":"greeting","locale":"it"}' | basenc --base64url | tr -d '=')

# Read one
curl "http://localhost:8080/dal/v1/translations/$ID"

# Update
curl -X PATCH "http://localhost:8080/dal/v1/translations/$ID" \
  -H "Content-Type: application/json" \
  -d '{"text":"Ciao!"}'

# Delete
curl -X DELETE "http://localhost:8080/dal/v1/translations/$ID"
```

The JSON field names are the **wire names** (after any `@JsonProperty` / naming strategy), exactly as the
entity serializes them in response bodies. A malformed Base64 segment or JSON that doesn't match the ID
class is rejected.

> **Tip:** the generated OpenAPI documentation shows a copy-pasteable Base64 example for the `id`
> parameter of every composite-key DAL in Swagger UI.

## Filtering (Query Language)

The `q` parameter uses [**Turkraft Spring Filter**](https://github.com/turkraft/springfilter) syntax for expressive
filtering without reinventing SQL. (Telaio builds its dynamic filtering on this library —
see [Acknowledgments](../README.md#acknowledgments).)

### Syntax

**Basic operators:**

- `field:value` — Equality (exact match)
- `field>value` — Greater than
- `field<value` — Less than
- `field>=value` — Greater than or equal
- `field<=value` — Less than or equal
- `field!=value` — Not equal

This section documents only the operators exercised by the Telaio integration tests. The library supports more
(LIKE/pattern matching, IN, null checks, functions, …) — see the
[Turkraft Spring Filter repository](https://github.com/turkraft/springfilter) for the full grammar.

**String values** must be quoted:

```
category:'electronics'
name:'Laptop'
```

**Numeric values** are unquoted:

```
price>500
quantity<=10
```

**Boolean values**:

```
available:true
discontinued:false
```

**Combining filters:**

- `and` — Both conditions must be true
- `or` — Either condition can be true
- Parentheses for grouping: `(category:'electronics' or category:'books') and price>100`

### Examples

```bash
# Exact match on a string field
curl "http://localhost:8080/dal/v1/products?q=category:%27electronics%27"

# URL-decoded: ?q=category:'electronics'

# Numeric comparison
curl "http://localhost:8080/dal/v1/products?q=price>500"

# Combined conditions
curl "http://localhost:8080/dal/v1/products?q=category:%27electronics%27%20and%20price>500%20and%20available:true"

# OR condition
curl "http://localhost:8080/dal/v1/products?q=category:%27electronics%27%20or%20category:%27books%27"

# Complex with grouping
curl "http://localhost:8080/dal/v1/products?q=(category:%27electronics%27%20or%20category:%27appliances%27)%20and%20price>1000"
```

### JSON Property Names

If your entity uses `@JsonProperty` to rename a field (e.g., `costPrice` → `cost_price`), the filter can use either the
Java property name or the JSON name:

```java
@JsonProperty("cost_price")
private Double costPrice;
```

Both work:

```bash
# Using Java property name
curl "http://localhost:8080/dal/v1/products?q=costPrice>100"

# Using JSON property name
curl "http://localhost:8080/dal/v1/products?q=cost_price>100"
```

### Error Handling

If the filter syntax is invalid, you get a **400 Bad Request** with a `ProblemDetail`:

```bash
curl "http://localhost:8080/dal/v1/products?q=((("
# 400 Bad Request
# ProblemDetail: "Malformed filter expression"
```

## Error Responses

All errors use the **RFC 9457 `ProblemDetail`** format with `application/problem+json` content type.

### 400 Bad Request (Validation)

Validation errors include field-level details:

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
      "field": "name",
      "rejectValue": null,
      "message": "Product name is required"
    },
    {
      "object": "product",
      "field": "price",
      "rejectValue": -1,
      "message": "Price must be greater than 0"
    }
  ]
}
```

Each `errors` entry carries the validated `object` name, the `field` that failed, the `rejectValue` (serialized as
`null` when absent), and the validation `message`.

### 403 Forbidden (Access Denied)

Generic response body per OWASP (no detail leak):

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "instance": "/dal/v1/products/1"
}
```

The actual reason is logged to `telaio-audit` (DENIED event) and application logs (DEBUG level), never exposed to the
client.

### 404 Not Found

**If the operation isn't exposed and the URI exposes no sibling method** (e.g. the item URI of a DAL declared
with `operations = {CREATE, READ}` only):

Deliberately empty body, so the response doesn't reveal that the operation exists:

```
404 Not Found
(empty body)
```

**If the DAL name is unknown — or the DAL is `internal = true`** (internal DALs are never registered on the web
boundary, so the response is identical to a nonexistent name and doesn't reveal their existence):

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "DAL service not found: products",
  "instance": "/dal/v1/products"
}
```

**If the entity doesn't exist:**

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Product was not found for id: [999]",
  "instance": "/dal/v1/products/999"
}
```

### 405 Method Not Allowed

If the operation exists but the HTTP method doesn't (e.g., DELETE on a read-only resource), the response includes an
`Allow` header:

```http
405 Method Not Allowed
Allow: GET
```

This happens when `@DalService(operations = {READ, READ_ONE})` excludes UPDATE and DELETE: a PATCH or DELETE on
`/dal/v1/articles/{id}` is rejected with `Allow: GET`, because READ_ONE (GET) is the only operation exposed on the
item URI.

### 500 Internal Server Error

```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "An unexpected error occurred",
  "instance": "/dal/v1/products"
}
```

The actual exception is **logged at WARN level** with the full stack trace, but never leaked to the client.

## Authentication and Authorization

### Basic Authentication

If Spring Security is configured (as in the showcase), requests can use HTTP Basic Auth:

```bash
curl -u username:password http://localhost:8080/dal/v1/products
```

The `Authorization` header is passed to `DalSecurityInterceptor`, which checks the principal's permissions via
`DalAuthAdapter`.

### Authorization Checks

Operations are gated by `DalAuthAdapter` methods:

- `authorizeCreate(Authentication)` — Can the principal create?
- `authorizeRead(Authentication)` — Can the principal list entities?
- `authorizeReadOne(Authentication, id)` — Can the principal read this specific entity?
- `authorizeUpdate(Authentication, id)` — Can the principal update this specific entity?
- `authorizeDelete(Authentication, id)` — Can the principal delete this specific entity?

If any check returns `false`, the response is **403 Forbidden** with a generic body.

### Field-Level Access Control (RBAC)

Even if CREATE is authorized, `DalRbacAdapter.filterInput()` may remove sensitive fields from the request body.
Similarly, `filterOutput()` may hide fields from the response.

Example: A USER can read a PRODUCT but cannot see the `costPrice` field (only DEVELOPER can).

## Content Negotiation

All endpoints return **`application/json`** (`Content-Type: application/json`).

Request bodies are also expected to be JSON. Sending other content types results in a **415 Unsupported Media Type**
error.

## Status Code Summary

| Code    | Scenario                                                  | Example                                                   |
|---------|-----------------------------------------------------------|-----------------------------------------------------------|
| **201** | Entity created successfully                               | POST /dal/v1/products                                     |
| **200** | Read or update successful                                 | GET /dal/v1/products/1, PATCH /dal/v1/products/1          |
| **204** | Delete successful, no body (update: edge case only)       | DELETE /dal/v1/products/1                                 |
| **400** | Validation failed or malformed input                      | Invalid filter, missing required fields                   |
| **403** | Access denied by `DalAuthAdapter` or RBAC                 | Principal not authorized                                  |
| **404** | DAL not found, operation not exposed, or entity not found | Unknown {dalName}, non-exposed operation, or invalid {id} |
| **405** | Operation not exposed, other methods available            | DELETE on a read-only DAL                                 |
| **500** | Server error                                              | Database down, application exception                      |

## OpenAPI Documentation

If `telaio-openapi` is on the classpath and `telaio.openapi.enabled=true`, each DAL is documented automatically in the
OpenAPI spec.

Visit the Swagger UI (if `springdoc-openapi-starter-webmvc-ui` is included):

```
http://localhost:8080/swagger-ui.html
```

Each DAL is listed as a collapsible group (if `telaio.openapi.tag-per-dal=true`). Expand it to see all five operations
documented with:

- Request body schema
- Response schema (including the entity's fields)
- Query parameters (`q`, `page`, `size`, `sort`)
- Status codes and error responses
- Authorization requirements (if `@DalSecurity` is present)

## Examples with Real Data

All examples below use the `products` DAL from the showcase.

### Create a product with validation

```bash
curl -X POST http://localhost:8080/dal/v1/products \
  -H "Content-Type: application/json" \
  -u developer:developer \
  -d '{
    "name": "Gaming Laptop",
    "price": 2499.99,
    "costPrice": 1800.00,
    "category": "electronics",
    "description": "High-end gaming laptop with RTX GPU"
  }'
```

Response: **201 Created**

```json
{
  "id": 5,
  "name": "Gaming Laptop",
  "price": 2499.99,
  "costPrice": 1800.00,
  "marginPercentage": 38.89,
  "category": "electronics",
  "description": "High-end gaming laptop with RTX GPU",
  "available": true
}
```

### List products filtered and sorted

```bash
curl -u developer:developer \
  "http://localhost:8080/dal/v1/products?q=category:%27electronics%27&sort=price,desc&page=0&size=5"
```

Response: **200 OK** (Page object with filtered results sorted by price descending)

### Update a product

```bash
curl -X PATCH http://localhost:8080/dal/v1/products/5 \
  -H "Content-Type: application/json" \
  -u developer:developer \
  -d '{"price": 2199.99}'
```

Response: **200 OK** with the updated entity (`AbstractDal.update` re-reads the entity after saving; **204 No
Content** is only a contract edge case, not the normal path)

### Delete a product

```bash
curl -X DELETE http://localhost:8080/dal/v1/products/5 \
  -u developer:developer
```

Response: **204 No Content**

### Authorization denied

```bash
curl -X POST http://localhost:8080/dal/v1/products \
  -H "Content-Type: application/json" \
  -u user:user \
  -d '{"name": "...", "price": 123}'
```

Response: **403 Forbidden** (if `DalAuthAdapter` denies CREATE for USER role)

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "instance": "/dal/v1/products"
}
```
