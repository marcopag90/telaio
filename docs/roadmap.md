# Telaio Roadmap

Deferred work items, tracked here so later iterations know what to do and why. Each item that has a concrete code anchor
also carries a `TODO(roadmap)` comment at the relevant spot — search the codebase for `TODO(roadmap)` to find them.

## 1. ObjectId id support (telaio-mongo)

`org.bson.types.ObjectId` is not usable as a DAL id type today: `DefaultSimpleTypePredicate`
(telaio-introspection) does not recognize it, so `DalIdCodec` (telaio-rest-contract) classifies it as a *complex*
id — Base64-encoded JSON — and no Jackson 3 (de)serializer exists for it. Until fixed, **`String` ids are the supported
identifier type** for Mongo entities (documented in [modules/mongo.md](modules/mongo.md)).

Plan:

- Extend `DefaultSimpleTypePredicate` to recognize `ObjectId` **without** adding a bson dependency to
  telaio-introspection (class-name check, or a pluggable predicate).
- Contribute a Jackson 3 `ObjectId` (de)serializer module from telaio-mongo (e.g. via a
  `Jackson3ObjectMapperBuilderCustomizer` / `JacksonModule` bean), so `objectMapper.convertValue(String, ObjectId)`
  works both in `DalIdCodec.decode` and on the client encode side (`DalUriFactory` in telaio-rest-client-shared — the
  codec must hold symmetrically).
- Related: filtering on a `String @Id` field through `q=` relies on Turkraft's `StringCustomObjectIdConverter`
  being registered in the active `ConversionService`; verify/document the by-id filter path when tackling this.

Code anchors: `MongoDal` (class-level TODO), `DefaultSimpleTypePredicate`, `DalIdCodec`.

## 2. Jackson 2 containment & removal (goal: Jackson 3 / `tools.jackson` only)

**Done (2026-08-24):** Turkraft Spring Filter **4.0.7** migrated the `mongo` artifact to Jackson 3
(`tools.jackson`) — upstream PR by this repo's maintainer. telaio-mongo dropped both containment measures:
the private Jackson 2 mapper in `JsonAwareFilterQueryConverter` (the transformer now receives the injected
Jackson 3 mapper) and the `telaioMongoJackson2ObjectMapper` bean in `TelaioMongoAutoConfiguration`
(4.0.7's `JsonNodeHelperImpl` constructor-injects a Jackson 3 `ObjectMapper`, which Boot provides).
Verified via `dependency:tree`: `com.fasterxml.jackson.core:jackson-databind` now reaches a Telaio
application only through springdoc/Swagger. (`com.fasterxml.jackson.annotation.*` remains as the
annotations package shared with Jackson 3 — never part of the problem.)

Residuals:

- **Swagger/springdoc** still uses Jackson 2 internally — nothing actionable until upstream drops it; track
  their releases.
- The Turkraft `mongo` jar (4.0.7 included) still ships an `application.properties` at its jar root that
  enables `MongoTemplate` DEBUG logging on every consumer — remaining upstream PR opportunity.

## 3. Turkraft mongo functional gaps (document-only for now)

- The `mongo-language` artifact is **empty** — the Mongo filter function vocabulary is only `size` / `today`
  beyond the standard operators, versus JPA's ~55 processors. Candidate for an upstream issue.
- Temporal comparisons are unreliable: filter values pass through `Document.parse`, so date literals become plain
  strings and never match BSON `Date` fields. Persists in 4.0.7 (the Jackson 3 migration kept the JSON
  intermediate); would be fixed upstream by transforming straight to `org.bson.Document` — open PR opportunity.
- `$expr` queries cannot use indexes (except limited equality cases): filtered reads are collection scans — documented
  as a performance characteristic in [modules/mongo.md](modules/mongo.md).

## 4. Showcase Mongo demo

Wire a Mongo-backed DAL into telaio-showcase (docker-compose Mongo service + Testcontainers integration test) to prove
jpa+mongo coexistence end-to-end: two backends, two transaction managers, one `/dal/v1` surface.

## 5. Transactions phase 2

- Document/demo a `MongoTransactionManager` setup (replica-set docker-compose) as the production-grade configuration.

**Decided (2026-08-23):** the `PassThroughTransactionManager` fallback (now in
`com.paganbit.telaio.core.transaction`) is the final design — telaio-core will not offer an
optional-transaction-manager mode. Wherever a backend has no real transaction manager, inject the pass-through.
