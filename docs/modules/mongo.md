# Telaio: Mongo Module

The Mongo module is the **MongoDB backend implementation** of the persistence-agnostic DAL abstraction, built on Spring
Data MongoDB. It delegates CRUD operations to a Spring Data Mongo repository and converts Turkraft filter expressions to
Mongo queries.

The core contract knows nothing about MongoDB — `Dal`/`AbstractDal` use only Spring Data's paging/sorting abstractions,
and a backend implements the `execute*` SPI.

## Purpose

- **Spring Data MongoDB integration:** Transparent delegation to your repository (plus `MongoOperations` for filtered
  reads — `MongoRepository` has no specification-executor analogue)
- **Filter-to-Mongo conversion:** Dynamic Turkraft filter queries → Mongo `Query` (`$expr`-based)
- **Type-safe repository definitions:** Minimal boilerplate interface declarations
- **Setter-based injection:** Concrete DAL classes need no constructor in Spring
- **Metadata extraction:** Entity mapping metadata and default sort order resolution
- **Coexistence-ready:** designed to live alongside telaio-jpa in the same application (dedicated, qualified transaction
  manager — see [Transactions](#transactions))

## Key Public Types

### Core DAL Implementation

| Type                       | Purpose                                                                                                                                                                                                                                                     |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MongoDal<E, I>`           | Extends `AbstractDal`, implements `MongoDalMetadata`. Delegates CRUD to `MongoDalRepository`, runs filtered reads through `MongoOperations`, converts filters via `FilterQueryConverter`. Setter-injects `repository`, `mongoOperations`, `queryConverter`. |
| `MongoDalRepository<E, I>` | Spring Data `@NoRepositoryBean` interface extending `MongoRepository`. Developers write: `interface XRepository extends MongoDalRepository<X, String> {}`. No custom methods needed.                                                                        |
| `MongoDalMetadata<E, I>`   | Read-only: `MongoDalRepository<E,I> getRepository()` + `MongoPersistentEntity<E> getPersistentEntity()`. Exposes the backing repository and Spring Data mapping metadata.                                                                                   |

### Support

| Type                            | Purpose                                                                                                                      |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `FilterQueryConverter`          | Contract: parsed Turkraft `FilterNode` + entity class → Mongo `Query` (replaceable by a user bean)                           |
| `JsonAwareFilterQueryConverter` | Default converter: rewrites `@JsonProperty` wire names to Java names, then builds an `$expr` query                           |
| `EntityDefaultSortResolver`     | Resolves the default sort (ascending by id property) from Spring Data mapping metadata                                       |
| `PassThroughTransactionManager` | No-op transaction manager used as fallback on standalone Mongo — provided by telaio-core (see [Transactions](#transactions)) |

### Configuration

| Type                           | Purpose                                                                                                                  |
|--------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `TelaioMongoAutoConfiguration` | Spring Boot autoconfiguration (conditional on `MongoOperations` and Turkraft's mongo transformer being on the classpath) |

## How Developers Use It

### 1. Define an Entity — use `String` ids

```java

@Document
public class Announcement {

    @Id
    private String id;

    private String title;
}
```

> **Why `String` ids:** Spring Data maps a `String` `@Id` onto `_id` (generating an ObjectId hex string when unset),
> and it round-trips cleanly through the `/dal/v1` wire contract — URLs stay plain
> (`/dal/v1/announcements/{hexId}`). `org.bson.types.ObjectId` as the id type is **not supported yet**: the
> `DalIdCodec` classifies it as a complex id (Base64-encoded JSON) and no Jackson 3 (de)serializer exists for it.
> ObjectId support is tracked on the [roadmap](../roadmap.md).

### 2. Define a Repository Interface

No implementation needed — Spring Data MongoDB generates it:

```java
public interface AnnouncementRepository
    extends MongoDalRepository<Announcement, String> {
}
```

### 3. Define a Mongo DAL Service

Extend `MongoDal` and annotate with `@DalService`. Spring's generic-aware autowiring supplies the repository:

```java

@DalService(name = "announcements")
public class AnnouncementDalService extends MongoDal<Announcement, String> {
}
```

The framework:

- Autowires `AnnouncementRepository` into the `repository` setter
- Autowires `MongoOperations` (the `MongoTemplate`) into the `mongoOperations` setter
- Autowires the `FilterQueryConverter` into the `queryConverter` setter
- Autowires the `telaioMongoTransactionManager` bean into the (qualified) `transactionManager` setter
- Calls `afterPropertiesSet()` to extract the generic `<E, I>` and validate

**No constructor needed.** Setter injection keeps the class clean. Lifecycle hooks (`finalizeBeforeCreate`,
`defaultFilter()`, `defaultSort()`, …) work exactly as on `JpaDal` — see the
[JPA module docs](./jpa.md#4-override-lifecycle-hooks).

### 4. Use Filtering

Clients send Turkraft filter expressions in the `q` parameter, exactly as with a JPA DAL:

```bash
curl 'http://localhost:8080/dal/v1/announcements?q=title:%27URGENT%27'
```

`MongoDal` converts this to a Mongo `Query` internally. The conversion produces an **`$expr`-wrapped aggregation
expression** (that is what the Turkraft mongo artifact emits), which has two practical consequences:

- **No index use:** `$expr` predicates generally cannot use indexes (except limited equality cases), so filtered reads
  are collection scans. Fine for moderate collections; measure before relying on it at scale.
- **Temporal values compare as strings:** filter literals pass through JSON, so comparisons against BSON `Date`
  fields do not match. Known upstream limitation, tracked on the [roadmap](../roadmap.md).

Also note the Mongo filter function vocabulary is thinner than JPA's (only `size` and `today` beyond the standard
operators — the upstream `mongo-language` artifact is empty).

## Transactions

MongoDB multi-document transactions require a **replica set** (or `mongos`) — a standalone `mongod` rejects them at the
server level. That is why Spring Boot never auto-configures a `MongoTransactionManager`. Since `AbstractDal`
requires a `PlatformTransactionManager`, telaio-mongo resolves this with a **dedicated, qualified bean** named
`telaioMongoTransactionManager` (constant: `MongoDal.TRANSACTION_MANAGER_BEAN_NAME`):

- **Default (standalone-friendly):** telaio-core's no-op `PassThroughTransactionManager` — operations run without
  transactional semantics. The delete pre-check and the removal do not share a snapshot (a TOCTOU window the JPA backend
  does not have), but a `@Version` attribute still protects delete-vs-concurrent-update: the version is part of the
  remove criteria, so a stale delete fails with `OptimisticLockingFailureException` (→ `409 Conflict`).
- **Opt-in full semantics:** declare a `MongoTransactionManager` bean (replica set required) — the autoconfiguration
  detects it and routes it to every `MongoDal` instead of the fallback:

  ```java
  @Bean
  MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
      return new MongoTransactionManager(factory);
  }
  ```
- **Full control:** declare your own `PlatformTransactionManager` bean *named* `telaioMongoTransactionManager` and it
  replaces the arrangement entirely.

> **Ambiguity rule:** the opt-in detection uses "exactly one `MongoTransactionManager` bean". If you declare more
> than one (e.g. multi-database setups), the autoconfiguration cannot pick and falls back to the no-op manager,
> logging a WARN — resolve it by declaring the bean you want under the `telaioMongoTransactionManager` name.

**Coexistence with telaio-jpa:** the qualified bean is registered with `defaultCandidate = false`, so it never
participates in plain by-type autowiring — `JpaDal` beans keep receiving Boot's JPA transaction manager, `MongoDal`
beans receive the Mongo one, and a mixed jpa+mongo application boots with no ambiguity.

## Jackson note

As of Turkraft Spring Filter **4.0.7** the upstream `mongo` artifact runs on Jackson 3 (`tools.jackson`), like the rest
of Telaio — no Jackson 2 type enters the classpath through telaio-mongo. (`com.fasterxml.jackson.annotation` remains as
the annotations package shared with Jackson 3; in a full Telaio application the only Jackson 2 arrival is
springdoc/Swagger's internal use.)

Be aware the Turkraft `mongo` jar ships an `application.properties` at its jar root that enables
`MongoTemplate` DEBUG logging on every consumer — override
`logging.level.org.springframework.data.mongodb.core.MongoTemplate` in your own configuration if it bothers you.

## Testing

Spring Boot 4 has no embedded Mongo (`@DataMongoTest` expects a live server). The module's own tests use Testcontainers'
`MongoDBContainer`, which boots a **single-node replica set** — so even real transactions are testable. See
`MongoDalIntegrationTest` for the pattern (a `@ServiceConnection` container bean).

## Configuration

No module-specific configuration properties. See [Configuration Reference](../configuration.md) for framework-wide
settings.

## See Also

- [Getting Started](../getting-started.md) — The 3-file recipe (entity + repository + `@DalService`)
- [JPA Module](./jpa.md) — The JPA counterpart (lifecycle hooks documented there apply here too)
- [Core Module](./core.md) — `AbstractDal` base class and lifecycle hooks
- [REST API Guide](../rest-api.md) — How filtering works at the HTTP boundary
- [Roadmap](../roadmap.md) — Deferred items: ObjectId ids, upstream Turkraft gaps
