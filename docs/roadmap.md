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
- `q=` filtering on ObjectId-typed fields works via `ObjectIdAwareFieldTypeResolver`, registered
  as the `@Primary` Turkraft `FieldTypeResolver` (maps `ObjectId` → `CustomObjectId`, whose
  annotation-driven `{"$oid": hex}` shape survives the internal-node-mapper rendering).
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
- The Turkraft `mongo` jar (4.0.7 included) still ships an `application.properties` at its jar root that
  enables `MongoTemplate` DEBUG logging on every consumer — remaining upstream PR opportunity. Note that a
  consumer cannot neutralize it from `application.yaml` (at the same location `.properties` takes precedence over
  `.yaml`, and Boot loads a single `classpath:/application.properties` — the first on the classpath): the override
  must live in the consumer's own `application.properties`, which then shadows the jar's file entirely (this is what
  telaio-showcase does). telaio-mongo deliberately ships no `application.properties` of its own — it would be the
  same anti-pattern.

## 3. Turkraft mongo functional gaps (document-only for now)

- The `mongo-language` artifact is **empty** — the Mongo filter function vocabulary is only `size` / `today`
  beyond the standard operators, versus JPA's ~55 processors. Candidate for an upstream issue.
- Temporal comparisons are unreliable: filter values pass through `Document.parse`, so date literals become plain
  strings and never match BSON `Date` fields. Persists in 4.0.7 (the Jackson 3 migration kept the JSON
  intermediate); would be fixed upstream by transforming straight to `org.bson.Document` — open PR opportunity.
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
