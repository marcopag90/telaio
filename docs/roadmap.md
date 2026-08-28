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
- The Turkraft `mongo` jar (up to 4.0.9) ships an `application.properties` at its jar root that
  enables `MongoTemplate` DEBUG logging on every consumer — **removed upstream on the #524 branch (2026-08-28),
  pending release**; until then the note below still applies. Note that a
  consumer cannot neutralize it from `application.yaml` (at the same location `.properties` takes precedence over
  `.yaml`, and Boot loads a single `classpath:/application.properties` — the first on the classpath): the override
  must live in the consumer's own `application.properties`, which then shadows the jar's file entirely (this is what
  telaio-showcase does). telaio-mongo deliberately ships no `application.properties` of its own — it would be the
  same anti-pattern.

## 3. Turkraft mongo functional gaps (document-only for now)

- The `mongo-language` artifact is **empty** — the Mongo filter function vocabulary is only `size` / `today`
  beyond the standard operators, versus JPA's ~55 processors. Candidate for an upstream issue.
- **Done (2026-08-28, pending the upstream release):** temporal comparisons now work — filter values used to pass
  through `Document.parse`, so date literals became plain strings and never matched BSON `Date` fields. Fixed upstream
  by this repo's maintainer ([springfilter#524](https://github.com/turkraft/springfilter/issues/524)): the `mongo`
  artifact gained a BSON-native `FilterBsonTransformer` and the `FilterQueryConverter` bean its README always
  documented; `Document`/`Query` filters are built from typed values (BSON dates, `ObjectId`s, `UUID`s). telaio-mongo
  now decorates that converter (`JsonAwareFilterQueryConverter`, mirroring telaio-jpa) instead of owning a converter
  SPI, and dropped `ObjectIdAwareFieldTypeResolver` (the BSON transformer converts literals to the field's
  declared type, so `ObjectId` fields need no marker). Verified by
  `JsonAwareFilterQueryConverterIntegrationTest.temporalFilterComparesAsBsonDate` and the showcase
  `NotificationCrudIT`. Ships with the Turkraft release that includes the fix (4.0.10 expected).
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

## 7. Filter-language conformance suite (JPA vs Mongo)

**Open — next feature branch, to start from `feature/mongo-dal` once the upstream release lands.**

**Why.** Telaio's promise is "the same `q=` works on every backend", but nobody verifies it systematically. Turkraft's
own suites (checked on 4.0.9, 2026-08-28) do not guarantee runtime behaviour: `core` tests only parse; `mongo` tests
compare the *shape* of the generated expression, not its execution (a test stays green even when MongoDB would reject
the query); `mongo-example` executes only map/DBRef cases against embedded Mongo; `jpa` executes on H2 but covers a
subset of operators (no `~`, `~~`, `in`, `not in`, `is not null`, `not`, no temporal/enum/UUID literals). Empirical
proof: four latent defects surfaced while implementing #524 — temporals compared as strings (for years), placeholder
processors never injected on any backend, `~~` emitting syntax invalid inside `$expr`, `xor` emitting a non-existent
`$xor` and missing from the imports until 4.0.9. Telaio's coverage is real but sample-based (wire-name rewriting,
ObjectId, temporals, one showcase `channel:'WEBHOOK'` case).

**What.** A `FilterLanguageConformanceIT` in telaio-showcase (failsafe: real Postgres + Mongo replica set, one DAL per
backend behind `/dal/v1`): a `@ParameterizedTest` table of `filter → expected ids`, run twice against a JPA DAL and a
Mongo DAL seeded with the **same dataset** (twin entities with string, integer, decimal, boolean, enum,
`Instant`/`LocalDate`, UUID, nested object, collection, one `@JsonProperty`-renamed field). Cover every operator
(`:` `!` `>` `>=` `<` `<=` `~` `~~` `in` `not in` `is null` `is not null` `is empty` `is not empty` `and` `or` `not`
`xor`, parentheses), literals per type, nested paths, the `*` wildcard, `size()`/`today()` where both backends support
them. Keep an explicit list of **expected divergences** (JPA-only functions, `~` semantics if they differ) so
differences become documentation, not surprises. Estimate: ~150 lines of IT plus the two entities/seeders, 1–2 days
including the divergence list. Turkraft's tests are not to be touched — this lives in telaio only.

**Context to resume.** Upstream PR for #524 opened from the fork branch `feature/524-bson-transformer` (single
squashed commit); telaio's `feature/mongo-dal` holds the uncommitted port (BOM on `4.0.10-SNAPSHOT`, decorator of
Turkraft's `FilterQueryConverter`, `ObjectIdAwareFieldTypeResolver` removed, jpa autoconfig aligned with the same
`@ConditionalOnMissingBean(..., ignored = ...)` back-off). Wait for the upstream release, set the BOM to the real
version (also `CHANGELOG.md`, `CLAUDE.md` versions and item 2 above), commit, then merge to `development` and branch
the conformance work from there.
