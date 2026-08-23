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

**Policy now:** Jackson 2 (`com.fasterxml.jackson.core:jackson-databind`) enters the classpath ONLY transitively via
`com.turkraft.springfilter:mongo` (compile-scope dependency) and is confined to two places in telaio-mongo:

- `JsonAwareFilterQueryConverter`'s private internal mapper (builds the intermediate Bson `JsonNode`);
- the `telaioMongoJackson2ObjectMapper` bean in `TelaioMongoAutoConfiguration` — required because Turkraft's
  eagerly-instantiated `JsonNodeHelperImpl` `@Service` constructor-injects a Jackson 2 `ObjectMapper`, which Spring Boot
  4 no longer auto-configures (without our bean, any consumer's context fails to start).

No `com.fasterxml` type may appear in any telaio public API signature (`FilterQueryConverter` exposes only
`FilterNode` + Spring Data `Query`). Note: `com.fasterxml.jackson.annotation.*` is the shared annotations package also
read by Jackson 3 — not part of the problem.

**Upstream inventory for the Turkraft PR** (to be opened by the maintainer of this repo): the Jackson 2 surface in
`springfilter mongo` 4.0.6 is:

- `FilterJsonNodeTransformer` — implements `FilterNodeTransformer<com.fasterxml.jackson.databind.JsonNode>`, constructor
  takes a Jackson 2 `ObjectMapper`;
- `JsonNodeHelper` / `JsonNodeHelperImpl` — `wrapWithMongoExpression(JsonNode)`; the impl constructor-injects the
  Jackson 2 `ObjectMapper` (the Boot-4 startup blocker described above);
- the 21 `*JsonNodeProcessor` classes;
- `FilterJsonNodeArgumentResolver` — builds `new BasicQuery(Document.parse(json.toString()))`.

Cleanest upstream fix: transform straight to `org.bson.Document` (removes Jackson entirely **and** fixes the
temporal-literals-as-strings issue below). Alternative: migrate to Jackson 3. Same PR opportunity: stop shipping an
`application.properties` at the jar root (it leaks `MongoTemplate` DEBUG logging and a flapdoodle property onto every
consumer's classpath).

**Swagger/springdoc:** still uses Jackson 2 internally — nothing actionable until upstream drops it; track their
releases.

Code anchors: `JsonAwareFilterQueryConverter` (mapper field + `Document.parse` TODOs),
`TelaioMongoAutoConfiguration.telaioMongoJackson2ObjectMapper`.

## 3. Turkraft mongo functional gaps (document-only for now)

- The `mongo-language` artifact is **empty** — the Mongo filter function vocabulary is only `size` / `today`
  beyond the standard operators, versus JPA's ~55 processors. Candidate for an upstream issue.
- Temporal comparisons are unreliable: filter values pass through `Document.parse`, so date literals become plain
  strings and never match BSON `Date` fields. Fixed for free by the `org.bson.Document` transform above.
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
