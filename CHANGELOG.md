# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### ⭐ New Features

- MongoDB DAL backend (`telaio-mongo`): `MongoDal` built on Spring Data MongoDB with Turkraft
  filter conversion (`$expr`-based), default sort resolution, and a dedicated qualified
  transaction manager (`telaioMongoTransactionManager`) with a no-op fallback for standalone
  MongoDB — designed to coexist with `telaio-jpa` behind the same `/dal/v1` surface.
- Metrics: `@TelaioMetricsDataSource` and `@TelaioMetricsTransactionManager` qualifiers select the
  DataSource (e.g. a dedicated metrics schema) and, optionally, the transaction manager the JDBC store
  uses in applications with several DataSources or transaction managers.

### 🐞 Bug Fixes

- Metrics: the JDBC store no longer looks up the application's `PlatformTransactionManager` by type — an
  application with multiple transaction managers failed to start, and a manager over a
  different DataSource was accepted silently. The store now uses a private JDBC transaction manager bound
  to its own DataSource; with several unmarked DataSources the context fails fast with guidance instead of
  persisting metrics into an arbitrary database.

### ⛔ Deprecations & Removals

- **Breaking:** `TypeUtil` (telaio-introspection) removed. The simple-type classification is now
  carried by the `DefaultSimpleTypePredicate` instance (aggregating `SimpleTypeContributor`
  beans). Internal-use class: consumers should inject the predicate bean instead. For the same
  reason the constructors of the framework-wiring classes `DalIdArgumentResolver`,
  `DalPathsGenerator` and `FilterParameterDescriber` now take the predicate.

### 📔 Documentation

- Showcase: `notifications`, a MongoDB-backed DAL running next to the JPA DALs with its own transaction
  manager — jpa+mongo coexistence on one `/dal/v1` surface.

### 🔨 Dependency Upgrades

- Turkraft Spring Filter 4.0.1 → 4.0.10 (BSON-native mongo filter conversion, `FilterQueryConverter` bean,
  `@DBRef`/`@DocumentReference` filtering, xor fix)

## [1.1.0] - 2026-07-29

Telaio DALs can now be consumed remotely: three new modules add a typed, blocking REST client
for the `/dal/v1` API, built on the same wire contract the server implements.

### ⭐ New Features

- Typed REST client for the DAL API (`telaio-rest-client`): `TelaioClientRegistry` hands out a
  `TelaioClient` per configured connection, and `dal(name, entityType, idType)` returns a typed
  `DalClient<E, I>` covering the full CRUD surface with paging, Spring Filter queries and sorting
  (`DalPage`, `DalPageRequest`, `DalSort`). Built on Spring's `RestClient`; it depends on neither
  `telaio-core` nor `telaio-web`, so a client application never pulls in the server modules.
- Client autoconfiguration bound to `telaio.rest-client.connections.<name>.{base-url,default-headers}`.
  Each connection inherits the application's `spring.http.client.*` settings (timeouts, SSL,
  `RestClientCustomizer` beans); per-connection authentication and headers are added with
  `TelaioRestClientCustomizer` beans. A single connection — or one named `default` — is also
  exposed as the primary `TelaioClient` bean.
- Frozen `/dal/v1` wire contract extracted into `telaio-rest-contract`, shared by server and
  client: `DalApiV1` path/parameter constants, `ValidationError`, and `DalIdCodec` — the same
  identifier encoding on both sides.
- HTTP responses are mapped to an unchecked `DalClientException` hierarchy — validation errors
  (`400` with the `errors` property), forbidden, not found, conflict, server faults, transport
  failures and "operation not exposed" (a bodiless `404`/`405`, i.e. a configuration mistake) are
  distinguishable without inspecting status codes. Reading a missing entity yields an empty
  `Optional`; mutating one throws.
- Client payloads follow RFC 7396 merge-patch semantics: a `Map` is sent as-is, so an explicit
  `null` clears a field, while `null`s in a DTO are stripped recursively and leave the stored
  value untouched.
- `telaio-rest-client-shared` holds the transport-neutral half of the client (paging types,
  exception tree, URI/payload/error mapping, configuration properties), ready to be reused by
  further transports.
- All three modules are published to Maven Central and version-managed by `telaio-bom`.

### 🔄 Improvements

- The read endpoint now returns Spring Data's `PagedModel` explicitly, so the page JSON
  (`content` + `page` metadata) is owned by Telaio. **Note:** applications that did not enable
  `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` previously received the raw
  `Page` serialization (page metadata at the root) and will now see the `content` + `page` shape
  on every DAL response. Telaio no longer imposes — nor depends on — the host's page
  serialization mode.
- JaCoCo coverage reporting is part of the build: `mvn test` / `mvn verify` produce a per-module
  report under `target/site/jacoco`, and `telaio-coverage-report` aggregates every library module
  into a single report (`telaio-showcase` is excluded). CI uploads the aggregate report as a build
  artifact and publishes a coverage badge on release tags.
- `swagger-annotations-jakarta` is a regular compile dependency of `telaio-rest-contract` (its
  version aligned through `telaio-bom`), so code compiling against `ValidationError` no longer
  produces `unknown enum constant Schema$RequiredMode` warnings.
- Dependabot keeps Maven dependencies and GitHub Actions up to date.

### 🐞 Bug Fixes

- A malformed `{id}` path segment — invalid Base64 for a composite key, or a value that cannot be
  converted into the DAL's identifier type — is now rejected with `400 Bad Request` (detail
  `"Malformed resource identifier"`) instead of surfacing as a `500`.

### 📔 Documentation

- README restructured around the server and client stacks, each with a module graph; new
  `docs/modules/rest-client.md` and `docs/modules/rest-contract.md`; the configuration guide
  documents the client connection properties, and the showcase guide the DAL-to-DAL round trip it
  now performs through the client.

### ⛔ Deprecations & Removals

- **BREAKING** for 1.0.x importers: `ValidationError` moved from
  `com.paganbit.telaio.web.validation` to `com.paganbit.telaio.rest.contract.v1` (module
  `telaio-rest-contract`, which arrives transitively with `telaio-web`) and is now an immutable
  record — read it through `object()`, `field()`, `rejectValue()`, `message()` instead of the
  `getX()` getters; the no-arg constructor and setters are gone. The JSON shape is unchanged, so
  only code that imports the type needs updating.

## [1.0.1] - 2026-07-09

### ⭐ New Features

- A syntactically malformed `q` filter expression is now rejected with `400 Bad Request`
  (RFC 9457 `ProblemDetail`, detail `"Malformed filter expression"`) instead of surfacing
  as a `500`; the `400` response is documented on the read operation in the generated
  per-DAL OpenAPI (`telaio-web`, `telaio-openapi`).

### 🔨 Dependency Upgrades

- Upgrade to Turkraft Spring Filter 4.0.1 — the Spring Boot 4-compatible line of the
  filter library (previously 3.2.5, built against Spring Boot 3.5).

### 📔 Documentation

- README: Maven Central badge and 1.0.0 dependency coordinates.

## [1.0.0] - 2026-07-06

First public release, available on Maven Central under the `com.paganbit` group id.

### ⭐ New Features

- Unified, persistence-agnostic Data Access Layer (`Dal` / `AbstractDal`) with dynamic
  property-map CRUD, validation, property merging, transactional hooks and a
  `@DalService` registry (`telaio-core`, `telaio-introspection`).
- Dynamic REST API (`/dal/v1/{dalName}`) with Turkraft Spring Filter queries, Spring Data
  pagination and RFC 9457 `ProblemDetail` error responses (`telaio-web`).
- Per-DAL exposure control: `@DalService(internal = true)` hides a DAL from every remote
  boundary, `@DalService(operations = {...})` restricts the exposed CRUD operations
  (`404`/`405` at the web boundary, omitted from OpenAPI).
- CRUD-level authorization (`DalAuthAdapter`) and field-level RBAC
  (`PropertyBasedDalRbacAdapter`, `JsonViewDalRbacAdapter`) on top of Spring Security
  (`telaio-security`).
- Opt-in auditing of DAL operations with logfmt / JSON Lines output on a dedicated logger
  category, MDC correlation and granular outcomes (SUCCESS / DENIED / VALIDATION /
  NOT_FOUND / CONFLICT / ERROR) (`telaio-audit`).
- Usage and latency metrics per DAL and operation with time-bucketed aggregation,
  in-memory and multi-vendor JDBC stores (PostgreSQL, MySQL, MariaDB, Oracle, SQL Server),
  an optional Micrometer path and the `telaiometrics` actuator endpoint (`telaio-metrics`).
  Client faults (validation, not-found, conflicts) are counted separately from service
  errors (`client_error_count`).
- Concrete per-DAL OpenAPI documentation replacing the generic templated operations
  (`telaio-openapi`).
- JPA/Hibernate backend (`JpaDal`) with generic-aware setter injection and
  filter-to-`Specification` conversion (`telaio-jpa`).
- `telaio-bom` Bill of Materials aligning all library modules and integrated third
  parties.
- Concurrent modification of a `@Version`-ed entity maps to `409 Conflict`.
- GitFlow release tooling and Maven Central publishing: signed artifacts with sources and
  javadoc jars, uploaded to the Central Portal from CI on release tags.

### 🐞 Bug Fixes

- `DELETE` enforces the DAL's `defaultFilter` and runs its visibility check inside the
  delete transaction (TOCTOU hardening); deleting an entity outside the filter now
  returns `404` (previously `204`).

[unreleased]: https://github.com/marcopag90/telaio/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/marcopag90/telaio/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/marcopag90/telaio/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/marcopag90/telaio/releases/tag/v1.0.0
