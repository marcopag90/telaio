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
- Core: `DalInvalidFilterException` (a `DalFailureKind.VALIDATION` client fault) for a well-formed `q=`
  filter the entity cannot honour, `JsonPropertyPathResolver.resolveJavaPath` reporting the first
  unresolvable segment of a field path, and `FilterNodes.fieldNodes` collecting the field references of a
  parsed filter.
- Security: field-level RBAC now governs the `q=` filter too. `DalRbacAdapter.canFilterOn(fieldPath, auth)`
  (new `default` hook, pass-through) is asked by `DalSecurityInterceptor` for every field a read filter references,
  before the read runs; a field the principal cannot read is rejected with the same generic 400
  (`"Invalid filter expression"`) as an unknown field — closing the inference channel where a hidden value
  (e.g. `cost_price`) could be worked out from the narrowed page. Both built-in adapters implement the same rule —
  a field is filterable exactly when it appears in the read response: `PropertyBasedDalRbacAdapter` requires a
  serialized property granted in the read readable map (exactly or through a granted descendant);
  `JsonViewDalRbacAdapter` requires the active read view on every declared property of the path, map values
  included. Both resolve the path under the wire name and the Java name of the property alike. Server-side
  `defaultFilter()`s are not affected. The
  rejection is raised as `DalFilterFieldNotReadableException` (core; a `DalInvalidFilterException`), which
  `telaio-audit`'s `SecurityDalAuditOutcomeClassifier` records as a **DENIED** event — for a field that exists but is
  hidden; a field the entity does not expose at all falls through to the DAL's strict validation and is audited as
  `VALIDATION` (identical generic 400 on the wire). `JsonPropertyPathResolver`'s
  path-walking rules (`isReferenceKeySegment`, `unwrapElements`, `isOpaque`, `isLeaf`) are now public.
- Security: field-level RBAC now governs the `sort=` keys too. `DalSecurityInterceptor` asks the same
  `DalRbacAdapter.canFilterOn` hook for every `Sort.Order` property of a read's pageable, before the read runs —
  a sort key the principal cannot read would otherwise leak the relative order of the hidden values
  (`sort=cost_price,desc&size=1`). The rejection is raised as `DalSortFieldNotReadableException` (core; a
  `DalInvalidSortException`), maps to the same generic 400 (`"Invalid sort parameter"`) as an unknown sort
  property, and is recorded as a **DENIED** audit event when the property exists but is hidden (an unknown property is
  audited as `VALIDATION` instead). The DAL's own `defaultSort()` is not affected.
- Core: `DalInvalidSortException` (a `DalFailureKind.VALIDATION` client fault) for a `sort=` property the read
  cannot honor, and `JsonFieldNameSortRewriter` translating caller-supplied sort properties from JSON wire
  names to Java property names (both spellings accepted, direction/case/null-handling preserved). New
  protected hook `AbstractDal.validateSortProperty(String)` lets each backend validate the resolved property:
  `JpaSortPropertyValidator` (JPA metamodel; deliberately stricter than the filter walk — no subtype
  attributes, no Map accessors, terminal segment must be singular) and `MongoSortPropertyValidator` (mapping
  context; same walk as the filter validator).

### 🐞 Bug Fixes

- Build: the library modules can be built with JDK 21 again. The enforcer's build-JDK floor followed a fixed
  `[25,)` inherited by every module; it now follows each module's `maven.compiler.release` (`[21,)` for the
  libraries, `[25,)` for `telaio-showcase`), as the README and CONTRIBUTING always documented.
- Filtering: a well-formed `q=` filter that references a field the entity does not expose or does not
  persist (`@Transient`, computed getter), or a function that is unknown or unsupported by the backend, is
  now a **400** (`"Invalid filter expression"`) on both the JPA and the Mongo backend — before, such
  filters were a 500 on JPA and a silently empty page on Mongo. Field paths are resolved against the
  entity's properties (JSON wire names and Java names, so server-side default filters keep working) and
  checked against the backend's mapping (JPA metamodel, Mongo mapping context); keys below a
  `Map`/`Object` property and the `$id`/`$ref`/`$db` keys of a stored reference are not checked. A literal
  that does not convert to the field's type (`quantity:'abc'`) stays a server fault (500) on both
  backends. Multi-pattern likes (`field ~ ['a*', 'b*']`) now honour `@JsonProperty` wire names too.
- Core: a read with an *unpaged, unsorted* `Pageable` no longer throws `UnsupportedOperationException`
  while applying the default sort (`Pageable.unpaged().getPageNumber()` is unsupported); it now stays
  unpaged and carries the DAL's `defaultSort()`.
- Sorting: a `sort=` property the entity does not expose or does not persist is now a **400**
  (`"Invalid sort parameter"`) on both backends — before, an unknown sort property was a 500
  (`PropertyReferenceException`) on JPA and a silently unsorted 200 on Mongo. Sort properties are now
  resolved like filter fields (JSON wire names and Java names alike), making the documented `DalSort`
  contract of the REST client ("property uses the JSON field names") actually hold: a sort on a
  `@JsonProperty`-renamed field used to fail on JPA and order by a missing path on Mongo.
- Metrics: the JDBC store no longer looks up the application's `PlatformTransactionManager` by type — an
  application with multiple transaction managers failed to start, and a manager over a
  different DataSource was accepted silently. The store now uses a private JDBC transaction manager bound
  to its own DataSource; with several unmarked DataSources the context fails fast with guidance instead of
  persisting metrics into an arbitrary database.

### ⛔ Deprecations & Removals

- **Breaking:** validation and JSON path resolution are now application-wide singletons. `TelaioCoreAutoConfiguration`
  registers one `dalJsonPropertyPathResolver` (`JsonPropertyPathResolver`), one `dalJsonFieldNameSortRewriter`
  (`JsonFieldNameSortRewriter`) and one `dalValidator` (`DalValidator`) bean — all `@ConditionalOnMissingBean` — in
  place of the per-DAL instances. Consequences for consumers:
  - `DalValidator` is de-generified: `DalValidator<T>.validate(T)` becomes `DalValidator.validate(Object target,
    Class<?> type)`; the default implementation is the new `DefaultDalValidator`.
  - `DalMapConverterValidator` is removed (map→entity conversion is a plain `ObjectMapper.convertValue` inside
    `AbstractDal.create`); `AbstractDal` no longer implements `DalValidator` and loses `setValidatorAdapter` /
    `getMapConverterValidator`. It now requires the sort rewriter and the validator (`setSortRewriter`,
    `setDalValidator`, checked in `afterPropertiesSet`) — tests that instantiate a DAL by hand must set both.
  - Constructors changed: `JsonAwareFilterSpecificationConverter` (JPA) and `JsonAwareFilterQueryConverter` (Mongo)
    take the shared `JsonPropertyPathResolver` (the Mongo one a `MappingContext`) instead of an `ObjectMapper`;
    `DalSecurityInterceptorProvider` takes the resolver and `DalSecurityInterceptor` a field-existence predicate.
  - `setObjectMapper` (on `PropertyBasedDalRbacAdapter` and `JsonViewDalRbacAdapter`) and the new `setPathResolver`
    (on `PropertyBasedDalRbacAdapter`) are now required `@Autowired` setters: a context without an `ObjectMapper` /
    `JsonPropertyPathResolver` bean fails to start where a missing bean used to be tolerated. The field defaults
    remain for use outside a container.
  - `telaio-core` now ships `spring-boot-jackson` (an `ObjectMapper` bean is always available); downstream modules
    dropped their own declaration. The former `TelaioOpenApiAutoConfiguration` resolver bean is gone — it consumes the
    shared one.
- **Breaking (behaviour):** under RBAC, a `q=`/`sort=` field that does not exist on the entity is no longer a denied
  attempt: it falls through to the DAL's strict validation (audit `VALIDATION`), while a field that exists but is
  hidden stays `DENIED`. The wire response is the identical generic 400 in both cases.
- **Breaking:** `TypeUtil` (telaio-introspection) removed. The simple-type classification is now
  carried by the `DefaultSimpleTypePredicate` instance (aggregating `SimpleTypeContributor`
  beans). Internal-use class: consumers should inject the predicate bean instead. For the same
  reason the constructors of the framework-wiring classes `DalIdArgumentResolver`,
  `DalPathsGenerator` and `FilterParameterDescriber` now take the predicate.

### 📔 Documentation

- Showcase: `notifications`, a MongoDB-backed DAL running next to the JPA DALs with its own transaction
  manager — jpa+mongo coexistence on one `/dal/v1` surface.

### 🔨 Dependency Upgrades

- Turkraft Spring Filter 4.0.1 → 4.1.0 (BSON-native mongo filter conversion, `FilterQueryConverter` bean,
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
