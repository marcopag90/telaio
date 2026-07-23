# Telaio: REST Contract Module

The rest-contract module codifies the **wire contract of the DAL REST API v1** (`/dal/v1`) as a
tiny, dependency-light artifact shared by the server boundary (`telaio-web`) and the remote client
(`telaio-rest-client`). Whatever both sides must agree on to interoperate lives here — so it cannot
drift apart within a version.

## Purpose

- Single source of truth for the `/dal/v1` **path and parameter constants** (including the
  `errors` ProblemDetail extension property name).
- The **ID path-segment codec** (`DalIdCodec`): simple IDs travel raw, composite IDs travel as
  URL-safe unpadded Base64 of their JSON. `telaio-web` decodes with it, `telaio-rest-client` encodes
  with it — parity by construction.
- The **validation-error payload** (`ValidationError`) serialized by the server on
  `400 Bad Request` and deserialized by the client.

## Key Public Types

| Type                 | Purpose                                                                                      |
|----------------------|----------------------------------------------------------------------------------------------|
| `DalApiV1`           | Constants: `BASE_PATH` (`/dal/v1`), `q`/`page`/`size`/`sort` parameter names, `errors` property |
| `DalIdCodec`         | `encode(id, idType)` / `decode(rawId, idType)` for the `{id}` path segment                    |
| `DalIdCodecException`| Neutral failure raised by the codec; each side wraps it into its own boundary exception       |
| `ValidationError`    | One field-level validation failure: `{object, field, rejectValue, message}` — `object` and `field` are always present, `rejectValue` and `message` are optional |

### Package layout

The artifact is split in two layers so that a future `v2` can coexist without disturbing v1:

- **`com.paganbit.telaio.rest.contract`** — version-agnostic root, for contract types with no
  byte-on-the-wire shape of their own. Today: `DalIdCodecException`.
- **`com.paganbit.telaio.rest.contract.v1`** — the frozen `/dal/v1` wire shape: `DalApiV1`,
  `DalIdCodec` and `ValidationError`.

The simple-vs-composite classification is delegated to `TypeUtil.isComplexType` from
`telaio-introspection` (`DefaultSimpleTypePredicate`), evaluated on the **declared** ID type —
the same input the server derives from `Dal#getIdClass()` and the typed client receives as
`Class<ID>`.

> **Note on paging parameter names:** `page`/`size`/`sort` are Spring Data's *default* web
> parameter names. Server applications must not customize them (`spring.data.web.pageable.*` /
> `spring.data.web.sort.*`) — doing so breaks the frozen v1 contract for every remote client.

## Compatibility policy

The v1 wire shape — paths, parameters, the `errors` extension, the page JSON, the ID-encoding
scheme **and the simple-type classification** — is **frozen**. Any change is a breaking API
change that requires a new contract version (`/dal/v2`, a new `v2` package in this artifact),
never an in-place edit. A failing client/server round-trip test is a server regression by
default. The page JSON (Spring Data's `PagedModel` shape) is owned by Spring Data: if a Spring
Data upgrade ever changes it, the **server compensates** (custom serialization) — deployed
clients are never required to follow.

The client module mirrors this convention on its side (`client.blocking.v1.DalClient`, frozen with
the wire) and is designed so that v1 and a future v2 coexist on the same connection — see
[Versioning & coexistence](./rest-client.md#versioning--coexistence).

## Dependencies

`telaio-introspection` (whose only compile dependency is `spring-core`), Jackson 3
(`jackson-databind`), jSpecify annotations, and — optional — swagger annotations (only consumed
when springdoc is present on the server; client applications are unaffected).

## No Configuration

The rest-contract module has no configuration properties.

## See Also

- [Client Module](./rest-client.md) — the typed remote client built on this contract
- [Web Module](./web.md) — the server boundary serving `/dal/v1`
- [REST API reference](../rest-api.md) — the full endpoint and filter documentation
