# Telaio: Client Module

The client module is a **lightweight, typed Java client** for the DAL REST API served by
`telaio-web` — the Telaio-to-Telaio integration path. One application configures the other as a named connection and
invokes its DALs through DTOs, with the wire encoding (paths, composite-ID Base64, paging, RFC 9457 errors) guaranteed
to match the server via the shared
[`telaio-rest-contract`](./rest-contract.md) artifact.

Deliberately **not** on the classpath: `telaio-core` and `telaio-web`. A client application depends on
`telaio-rest-client` (plus Spring's `RestClient` stack, via `spring-boot-starter-restclient`); the transport-neutral
[`telaio-rest-client-shared`](#module-split) artifact arrives transitively.

## Key Public Types

| Type                                            | Purpose                                                                             |
|-------------------------------------------------|-------------------------------------------------------------------------------------|
| `TelaioClientRegistry`                          | Registry over the configured connections: `get("billing")` → `TelaioClient`         |
| `TelaioClient`                                  | A client bound to one remote application: `dal(name, entityType, idType)`           |
| `DalClient<E, I>` (package `…client.blocking.v1`) | Typed handle on one remote DAL: `create` / `read` / `readOne` / `update` / `delete` |
| `DalPage<T>` / `DalPageRequest` / `DalSort`     | Paging and sorting, mirroring the server's page JSON (in `telaio-rest-client-shared`) |
| `TelaioRestClientCustomizer`                    | Per-connection `RestClient.Builder` hook (authentication, SSL, interceptors)        |
| `TelaioRestClient`                              | The blocking implementation: `create(RestClient[, ObjectMapper])`                   |
| `DalClientException` tree                       | Unchecked exceptions mapping the server's error contract (in shared; see below)     |

## Module split

The client is split into two artifacts along the transport axis, so that the blocking client and the planned reactive
sibling share everything that carries no transport dependency:

- **`telaio-rest-client-shared`** (transport-neutral; depends on `telaio-rest-contract`, **never** on
  `spring-boot-starter-restclient`) owns:
  - the root package — paging/sorting value types `DalPage`, `DalPageRequest`, `DalSort`;
  - `…client.exception` — the whole `DalClientException` tree (RFC 9457 mapping, no I/O types);
  - `…client.internal` — URI building, payload (de)serialization and error mapping (`DalUriFactory`,
    `DalPayloadCodec`, `DalErrorMapper`, `DalFilterStringConverters`);
  - `…client.config` — the `telaio.rest-client.connections.*` binding (`TelaioRestClientProperties`) and the
    primary-connection condition (`SingleConnectionCondition`).
- **`telaio-rest-client`** (blocking) owns everything under `…client.blocking`: the `TelaioClient` hub,
  `TelaioClientRegistry`, `TelaioRestClientCustomizer`, the `TelaioRestClient`/`DalRestClient` implementation, the
  `…blocking.v1` `DalClient` contract, and the `…blocking.autoconfigure` autoconfiguration. `RestClient` appears only
  here.

## Configuration

```yaml
telaio:
  rest-client:
    connections:
      billing:
        base-url: https://billing.example.com
        default-headers:
          X-Tenant: [ acme ]
      inventory:
        base-url: https://inventory.example.com
```

| Property                                                  | Description                                   |
|-----------------------------------------------------------|-----------------------------------------------|
| `telaio.rest-client.connections.<name>.base-url`          | Base URL of the remote application (required) |
| `telaio.rest-client.connections.<name>.default-headers.*` | Static headers sent with every request        |

Every connection is resolved through the `TelaioClientRegistry`. When exactly one connection is configured — or one is
named **`default`** — it is also exposed as the primary `TelaioClient`
bean, injectable directly.

## Usage

```java
record Product(Long id, String name, BigDecimal price) {
}   // DTO mirroring the remote entity

@Service
class CatalogSync {

    private final DalClient<Product, Long> products;

    CatalogSync(TelaioClientRegistry registry) {
        this.products = registry.get("billing").dal("products", Product.class, Long.class);
    }

    void sync() {
        DalPage<Product> page = products.read("price>10",
            DalPageRequest.of(0, 50).withSort(DalSort.asc("name")));
        Optional<Product> one = products.readOne(42L);
        products.update(42L, Map.of("price", new BigDecimal("9.90")));
    }
}
```

- **Payloads** (`create`/`update`) follow JSON Merge Patch semantics (RFC 7396): a `Map` is sent as-is (`null` values
  preserved — explicit null-set), a DTO is sent with `null` fields dropped at every nesting level. *To explicitly null a
  field, pass a `Map`.*
- **Composite keys**: declare the ID class (`dal("translations", Translation.class,
  TranslationId.class)`) and the client encodes it exactly as the server decodes it (URL-safe Base64 JSON, shared
  `DalIdCodec`).
- **Response leniency**: entities are deserialized ignoring unknown members and tolerating absent ones — field-level
  RBAC on the server may strip fields per caller, and newer servers may add fields. DTOs must tolerate absent members
  (nullable components).
- **`readOne` → `Optional.empty()`** on not-found (mirrors the server-side `Dal.readOne`); mutations on a missing target
  throw `DalClientNotFoundException`. An `update` answered `204`
  yields empty too — the update *was applied* but the result is no longer visible to the caller.

### Authentication

Nothing auth-specific is modeled — authentication is whatever the remote application requires, plugged in at the
`RestClient` level:

```java

@Bean
TelaioRestClientCustomizer billingAuth() {
    return (name, builder) -> {
        if ("billing".equals(name)) {
            // Works directly on Spring's RestClient.Builder, on top of the Boot-inherited
            // configuration (timeouts, SSL, observation, RestClientCustomizers).
            builder.requestInterceptor(
                new BasicAuthenticationInterceptor("svc", "secret"));
        }
    };
}
```

Spring Boot's autoconfigured `RestClient.Builder` is used per connection, so `spring.http.client.*`
(timeouts, SSL) and application-wide `RestClientCustomizer` beans apply automatically.

### Programmatic construction

Outside Spring (or for connections unknown at configuration time), hand in a ready-made
`RestClient` — pure composition, everything transport-related (base URL included) configured through Spring's own API:

```java
TelaioClient client = TelaioRestClient.create(RestClient.builder()
    .baseUrl("https://billing.example.com")
    .defaultHeader("X-Tenant", "acme")
    .build());
// overload: TelaioRestClient.create(restClient, objectMapper)
```

The `RestClient` **must** carry a base URL (requests are relative templates resolved against it). Spring exposes no way
to inspect a built client, so a missing base URL surfaces on the first invocation with a descriptive exception rather
than at `create(...)` time.

## Error model

All exceptions are unchecked, rooted at `DalClientException`. Every non-2xx response raises a
`DalClientResponseException` (carrying `statusCode()` and the parsed `problemDetail()` when the body is
`application/problem+json`):

| Server response                              | Exception                                                    |
|----------------------------------------------|--------------------------------------------------------------|
| `400` + `errors` extension                   | `DalClientValidationException` (`errors()`)                  |
| `400` (e.g. malformed `q` filter)            | `DalClientBadRequestException`                               |
| `403`                                        | `DalClientForbiddenException`                                |
| `404` with problem body                      | `DalClientNotFoundException`                                 |
| `404`/`405` bodyless (operation not exposed) | `DalClientOperationNotExposedException` (`allowedMethods()`) |
| `409` (optimistic lock)                      | `DalClientConflictException`                                 |
| `5xx`                                        | `DalClientServerException`                                   |
| I/O failure (no response)                    | `DalClientTransportException`                                |

## Reactive-readiness

`RestClient` is confined to `telaio-rest-client`'s `…client.blocking` packages. Everything transport-neutral — URI
construction, payload conversion, error mapping, the exception tree, the paging value types and the connection
properties — already lives in the separate **`telaio-rest-client-shared`** artifact (see [Module split](#module-split)).
The planned `telaio-rest-client-reactive` sibling (WebClient, `Mono`/`Flux` signatures) depends on
`telaio-rest-client-shared`, excludes `spring-boot-starter-restclient`, binds the same `telaio.rest-client.connections.*`
properties, and adds its own `…client.reactive` packages mirroring `…client.blocking`.

## Versioning & coexistence

The client mirrors the [contract module's](./rest-contract.md#compatibility-policy) package convention: everything
pinned to one wire contract version lives in a versioned package, everything else is shared across versions.

- **`com.paganbit.telaio.rest.client.blocking.v1`** holds `DalClient` — the client-side mirror of `contract.v1`. Like
  the wire contract it is **frozen**: its operations and signatures never change in place. A future `/dal/v2` would ship
  as a sibling `blocking.v2` package (mirroring a new `contract.v2`), never as an edit to `blocking.v1`.
- **`TelaioClient` is the version-neutral hub.** Each accessor is pinned to one contract version — `dal(...)` to
  `/dal/v1` — and a v2 would arrive as a *new* accessor (e.g. `dalV2(...)`) on the same interface, returning the
  `blocking.v2` handle.
- **Connections carry no version.** `telaio.rest-client.connections.<name>` configures only base URL and headers; the
  `/dal/v{n}` prefix is appended by each versioned handle. Both versions therefore coexist on the **same connection and
  the same injected `TelaioClient`**, with no configuration change — call sites migrate from `dal(...)` to `dalV2(...)`
  one at a time.
- Paging/sorting inputs (`DalPage`, `DalPageRequest`, `DalSort`) and the exception tree are version-agnostic and
  transport-agnostic (they live in `telaio-rest-client-shared`); the registry is version-agnostic.

## See Also

- [REST Contract Module](./rest-contract.md) — the shared wire contract and compatibility policy
- [Web Module](./web.md) — the server side of the same wire
- [REST API reference](../rest-api.md) — the `q` filter language and endpoint semantics
