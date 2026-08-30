# Telaio Roadmap

Deferred work items, tracked here so later iterations know what to do and why. Each item that has a concrete code anchor
also carries a `TODO(roadmap)` comment at the relevant spot — search the codebase for `TODO(roadmap)` to find them.

## 1. ObjectId id support (telaio-mongo)

**Done (2026-08-24):** `org.bson.types.ObjectId` is now a supported DAL id type, travelling as a
plain hex string on the `/dal/v1` wire. Implemented through a type-safe contribution mechanism:

- `DefaultSimpleTypePredicate` (telaio-introspection) accepts contributed types at construction;
  the new `SimpleTypeContributor` SPI lets modules contribute types as beans, aggregated by
  `TelaioCoreAutoConfiguration` into the shared predicate — introspection gained no bson
  dependency, not even test-scoped. The static `TypeUtil` was removed (it could diverge from the
  aggregated classification) — **breaking change**, to be called out in the next release notes.
- telaio-mongo contributes `ObjectId` and registers `ObjectIdJacksonModule` (Jackson 3, hex in
  both directions), picked up by Boot's Jackson autoconfiguration; `DalIdCodec`, the web id
  resolver and the OpenAPI generators consume the aggregated predicate.
- `q=` filtering on ObjectId-typed fields originally worked via `ObjectIdAwareFieldTypeResolver`, a
  `@Primary` Turkraft `FieldTypeResolver` decorator (`ObjectId` → `CustomObjectId`); removed on 2026-08-28
  once the BSON-native converter arrived: the hex literal is converted to the field's declared `ObjectId` type and
  emitted as a real BSON ObjectId, no marker needed (guarded by
  `JsonAwareFilterQueryConverterIntegrationTest.objectIdFieldFilterMatchesPersistedDocuments`; see item 3).
- Remote clients addressing an ObjectId-backed DAL should declare `String` as the client-side id
  type (wire-identical hex); see the rest-contract compatibility note.

## 2. Jackson 2 containment & removal (goal: Jackson 3 / `tools.jackson` only)

**Done (2026-08-24):** Turkraft Spring Filter **4.0.7** migrated the `mongo` artifact to Jackson 3
(`tools.jackson`) — upstream PR by this repo's maintainer. telaio-mongo dropped both containment measures:
the private Jackson 2 mapper in `JsonAwareFilterQueryConverter` (the transformer now receives the injected
Jackson 3 mapper) and the `telaioMongoJackson2ObjectMapper` bean in `TelaioMongoAutoConfiguration`
(4.0.7's `JsonNodeHelperImpl` constructor-injects a Jackson 3 `ObjectMapper`, provided by Boot's
Jackson autoconfiguration — telaio-mongo now declares `spring-boot-jackson` so the bean exists on
every consumer's classpath).
Verified via `dependency:tree`: `com.fasterxml.jackson.core:jackson-databind` now reaches a Telaio
application only through springdoc/Swagger. (`com.fasterxml.jackson.annotation.*` remains as the
annotations package shared with Jackson 3 — never part of the problem.)

Residuals:

- **Swagger/springdoc** still uses Jackson 2 internally — nothing actionable until upstream drops it; track
  their releases.
- The Turkraft `mongo` jar up to 4.0.9 shipped an `application.properties` at its jar root that enabled
  `MongoTemplate` DEBUG logging on every consumer — **removed upstream in 4.1.0 (2026-08-29, #527)**. The showcase's
  own `application.properties` override (the only way to neutralize it: at the same location `.properties` takes
  precedence over `.yaml`, and Boot loads a single `classpath:/application.properties` — the first on the classpath)
  is now redundant and can be dropped. telaio-mongo deliberately never shipped an `application.properties` of its
  own — it would be the same anti-pattern.

## 3. Turkraft mongo functional gaps (document-only for now)

- The `mongo-language` artifact is **empty** — the Mongo filter function vocabulary is only `size` / `today`
  beyond the standard operators, versus JPA's ~55 processors. Candidate for an upstream issue.
- **Done (2026-08-28, shipped in Turkraft 4.1.0):** temporal comparisons now work — filter values used to pass
  through `Document.parse`, so date literals became plain strings and never matched BSON `Date` fields. Fixed upstream
  by this repo's maintainer ([springfilter#524](https://github.com/turkraft/springfilter/issues/524)): the `mongo`
  artifact gained a BSON-native `FilterBsonTransformer` and the `FilterQueryConverter` bean its README always
  documented; `Document`/`Query` filters are built from typed values (BSON dates, `ObjectId`s, `UUID`s). telaio-mongo
  now decorates that converter (`JsonAwareFilterQueryConverter`, mirroring telaio-jpa) instead of owning a converter
  SPI, and dropped `ObjectIdAwareFieldTypeResolver` (the BSON transformer converts literals to the field's
  declared type, so `ObjectId` fields need no marker). Verified by
  `JsonAwareFilterQueryConverterIntegrationTest.temporalFilterComparesAsBsonDate` and the showcase
  `NotificationCrudIT`. Shipped in Turkraft 4.1.0 (2026-08-29).
- `$expr` queries cannot use indexes (except limited equality cases): filtered reads are collection scans — documented
  as a performance characteristic in [modules/mongo.md](modules/mongo.md).

## 4. Showcase Mongo demo

**Done (2026-08-25):** telaio-showcase now runs a Mongo-backed DAL next to its JPA ones —
`NotificationDalService` (`notifications`, `MongoDal<Notification, String>`, `@Version`, `@DalAudit`,
metrics on) — proving jpa+mongo coexistence end-to-end: two backends, two transaction managers
(`JpaTransactionManager` for the JPA DALs, a real `MongoTransactionManager` for the Mongo one; asserted by
`TelaioShowcaseApplicationTests.twoBackendsUseTwoTransactionManagers`), one `/dal/v1` surface. The
`compose.yaml` gained a `mongo` service, `TestcontainersConfiguration` a second `@ServiceConnection`
container, and `NotificationCrudIT` covers the DAL over HTTP (CRUD, `q=` filtering, validation, 404,
401, audit). Original goal: docker-compose Mongo service + Testcontainers integration test.

## 5. Transactions phase 2

**Done (2026-08-25):** the production-grade `MongoTransactionManager` setup is documented in
[modules/mongo.md](modules/mongo.md#transactions) (single-node replica-set compose service whose healthcheck
runs `rs.initiate` and gates on the writable primary; the mixed-application rule "declare the manager under
`MongoDal.TRANSACTION_MANAGER_BEAN_NAME` with `defaultCandidate = false`" — a plain bean would satisfy Boot's
`@ConditionalOnMissingBean(TransactionManager.class)`, so the `JpaTransactionManager` would never be created
and the JPA DALs would bind to the Mongo manager) and
demoed by the showcase (`compose.yaml`, `config/MongoConfiguration`). Real-transaction proof:
`MongoDalTransactionIntegrationTest` (telaio-mongo) — delete rollback when `finalizeAfterDelete` throws,
commit otherwise. While doing this, four docs/comments claiming Testcontainers' `MongoDBContainer` boots a
replica set by default were corrected: in Testcontainers 2.x `org.testcontainers.mongodb.MongoDBContainer`
is a standalone `mongod` and `withReplicaSet()` is opt-in (only the deprecated
`org.testcontainers.containers.MongoDBContainer` is replica-set-by-default).

**Decided (2026-08-23):** the `PassThroughTransactionManager` fallback (now in
`com.paganbit.telaio.core.transaction`) is the final design — telaio-core will not offer an
optional-transaction-manager mode. Wherever a backend has no real transaction manager, inject the pass-through.

## 6. telaio-metrics JDBC store and multiple transaction managers

**Done (2026-08-26):** the JDBC store no longer looks up the application's transaction manager or relies on a
single by-type `DataSource`. Two Batch-style qualifiers were added to telaio-metrics — `@TelaioMetricsDataSource`
(the DataSource holding the metrics table, e.g. one whose default schema is dedicated to metrics) and
`@TelaioMetricsTransactionManager` (optional override, JTA/XA) — and the resolution is: marked DataSource → else the
single/`@Primary` one → else fail fast with the candidate bean names and the fix spelled out (decided over a silent
in-memory fallback). The transaction manager defaults to a private `JdbcTransactionManager` bound to that
DataSource, never registered as a bean (single private consumer; a default-candidate bean would also make Boot's
`JpaTransactionManager` back off); the store writes only on its own flusher thread, so the application's managers
are irrelevant to it, and a flush forced from inside a caller's transaction joins that transaction. Documented in
[modules/metrics.md](modules/metrics.md#choosing-the-datasource-and-transaction-manager). Original problem:
`ObjectProvider.getIfAvailable()` failed the context with two default-candidate transaction managers and silently
accepted a manager over a different DataSource — surfaced by the Block 2 review on 2026-08-25.

## 7. Filter-language integration tests per backend + aligned invalid-filter errors

**Done (2026-08-28, branch `feature/filter-language-conformance`; verified against Turkraft 4.1.0, which shipped
#524/#527 on 2026-08-29).**

**Why.** Turkraft's own suites (checked on 4.0.9) do not guarantee runtime behaviour: `core` tests only parse;
`mongo` tests compare the *shape* of the generated expression, not its execution; `jpa` executes on H2 but covers a
subset of operators. Four latent defects surfaced while implementing #524 (temporals compared as strings, placeholder
processors never injected, `~~` invalid inside `$expr`, a non-existent `$xor`). Telaio's coverage was sample-based.

**What was delivered** (re-scoped from the original "twin entities behind `/dal/v1`" idea: the goal is to verify that
each backend *executes* the Turkraft vocabulary it supports, not cross-backend equivalence; test code only, surefire,
no user-facing docs):

- `JpaDalFilterLanguageIntegrationTest` (telaio-jpa, H2, 133 cases) and `MongoDalFilterLanguageIntegrationTest`
  (telaio-mongo, Testcontainers, 105 cases): a six-row fixture with every field kind, a `filter → expected codes`
  table run through the public `JpaDal`/`MongoDal.read()` (autoconfigured `@Primary` converter, real `FilterBuilder`,
  real transaction manager). JPA covers the 21 core definitions plus 43 of the 45 `jpa-language` functions
  (`jsonText` is Postgres-only, `countDistinct` has no processor — asserted as rejected); Mongo covers the 21 core
  definitions plus `Map` keys, `@DBRef` `$id`/`$ref`, `ObjectId` and `UUID` fields. Each module pins its own
  semantics (`today()` = day name vs BSON date; `~` with live SQL `%`/`_` vs regex-escaped; array-vs-scalar `$eq`;
  `is empty` array-only on Mongo). The class Javadoc of each test lists the pinned semantics and fixture rules.
- **Aligned errors:** core's `JsonFieldNameFilterRewriter` is strict on bean-typed paths (`JsonPropertyPathResolver
  .resolveJavaPath`; unchecked after `Map`/`Object`/`JsonNode` and on the `$id`/`$ref`/`$db` reference keys; JSON
  names, Java names and the Java names of Jackson-hidden fields accepted, so default filters keep working) and throws
  `DalInvalidFilterException`; both decorators wrap `UnsupportedOperationException` (function without processor)
  into it; the web controller wraps the parser's `UnsupportedOperationException` for an unknown function name the
  same way; `TelaioWebExceptionHandler` maps it to 400 `"Invalid filter expression"`; `DalFailureKind` classifies it
  as `VALIDATION`. **Decision (2026-08-29): a literal that does not convert to the field's type stays a server fault
  (500) — uniformly on both backends** (JPA: `DataAccessException` from the SQL `CAST` or from Hibernate's enum check;
  Mongo: `ConversionFailedException`/IAE from the BSON transformer; the decorators deliberately do not intercept
  them). Pinned by `unconvertibleLiteralIsAServerFaultOnThisBackend` in both ITs and end-to-end by `DalApiErrorsIT`.
- Latent gap fixed: library modules run surefire only, so `JsonAwareFilterSpecificationConverterIT` had never
  executed — renamed `*IntegrationTest`.

**Turkraft findings (upstream candidates; #527 shipped in 4.1.0 without them):** (1) JPA
`FilterExpressionTransformer.transformInput` swallows `ConversionFailedException` and falls back to the raw literal,
turning a bad literal into a SQL `CAST` failing at execution — a fork rethrow was tried and **dropped** (2026-08-29):
telaio keeps unconvertible literals as a uniform 500 instead; (2) multi-pattern like `f ~ ['a','b']` uses raw SQL patterns on JPA (no `*`
translation, no `%…%` wrap) but `*` regexes on Mongo; (3) `countDistinct` has a definition but no JPA processor;
(4) `locate()` requires all three arguments although the README shows two; (5) Mongo embeds string literals raw in
the `$expr` (`q=name:'$code'` compares two fields) — `{$literal: …}` would be safer; (6) under an `Object`-typed
(schemaless) property a numeric literal does not match on Mongo (`payload.nested.level : 2` → no rows, while
`payload.nested.tag : 'deep'` matches — the target type is unresolvable, literal handling to be investigated upstream).

**Residual.** Write-only (`@JsonProperty(access = WRITE_ONLY)`) and `@JsonIgnore`d properties remain filterable by
name (pre-existing behaviour, kept so default filters and in-process callers do not break) — see item 8.

## 8. Field-level authorization of `q=` filters

**Open.** Field-level RBAC prunes *output* (`DalRbacAdapter.filterOutput`), but nothing inspects the `FieldNode`s of a
filter: a principal with READ can filter on a property the adapter hides from them (`cost_price > 100`, bisection) and
infer its value from the narrowed page. Surfaced by the security review of item 7 (2026-08-28). Candidate design: a
`DalRbacAdapter` hook receiving the referenced JSON paths (after the JSON-name rewrite), applied in
`DalSecurityInterceptor` before the read, rejecting references outside the role's readable set with
`DalInvalidFilterException` (same 400 as an unknown field, so nothing is disclosed).

## 9. Reject filters on serialized-but-not-persisted properties (JPA vs Mongo)

**Done (2026-08-29).** The strict field check of item 7 resolves `q=` paths against the entity's *Jackson* view (it
lives in telaio-core, which knows nothing about persistence). A property that is serialized but not stored —
`@Transient` with `USE_TRANSIENT_ANNOTATION` disabled, or a computed getter; e.g. showcase `Product.profit` — therefore
passes the check and fails only inside the backend, differently: JPA → Hibernate `IllegalArgumentException` on
`root.get("profit")` → `InvalidDataAccessApiUsageException` → **500**; Mongo → `{$expr: {$gt: ["$profit", 10]}}` on a
missing path → **200, empty page**. Was the last JPA/Mongo divergence in error classification.

**Delivered: both → 400** (`DalInvalidFilterException`, same generic body as an unknown field; the only extra bit a
client learns is "stored vs computed" for a property already visible on the wire — the wider field-existence oracle
through Java names is item 8's concern). The check is **per backend**, in the two converter decorators, after the
JSON-name rewrite — `JpaFilterFieldValidator` (metamodel, incl. Hibernate's subtype attributes; `Map` attributes only
through `key(s)`/`value(s)`) and `MongoFilterFieldValidator` (the mapping context of the `MongoOperations` the DALs
persist through; `Map`/`Object` sub-documents are schemaless and unchecked, a `@DBRef` only through `$id/$ref/$db`),
both walking the `FieldNode`s collected by core's `FilterNodes.fieldNodes`. Covered by `JpaFilterFieldValidatorTest` /
`MongoFilterFieldValidatorTest`, `profit > 10` / `lines.subtotal > 1` in both filter-language ITs, unit cases in the two
converter tests, and `q=profit>10` → 400 on showcase `products`. Side fix: `JsonFieldNameFilterRewriter` now rewrites
`CollectionLikeNode` (`field ~ ['a*','b*']`), which it previously skipped. Original design notes:

- telaio-jpa `JsonAwareFilterSpecificationConverter.toPredicate`: walk every `FieldNode` path against the JPA metamodel
  (`root.getModel()`, `ManagedType#getAttribute` per segment; unwrap `PluralAttribute` element types; stop at
  `Map`-valued attributes — `key`/`value` accessors — and at basic types).
- telaio-mongo `JsonAwareFilterQueryConverter.convert`: walk against the Spring Data
  `MappingContext`/`MongoPersistentEntity` (`getPersistentProperty` per segment; unwrap collections; stop at `Map`
  properties, `@DBRef` targets (`$id/$ref/$db`) and simple types; `id` → `_id` is Turkraft's job).
- Tests: `profit > 10`-style cases in both filter-language ITs (a `@Transient` field in the JPA fixture, a getter-only
  property in the Mongo fixture) asserting `DalInvalidFilterException`; end-to-end `q=profit>10` → 400 on `products`
  in `DalApiErrorsIT`; unit tests for the two walkers (opaque segments, plural paths, unknown nested attribute).
- Docs: the "known limitation" sentence in `CHANGELOG.md` and the residual note of item 7 were dropped.
