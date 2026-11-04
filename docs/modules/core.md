# Telaio: Core Module

The core module defines the DAL abstraction, registration, and bean lifecycle. Every other module depends on it
directly.

## Purpose

- **DAL contract:** five CRUD operations via dynamic property maps
- **Persistence-agnostic:** the contract uses only Spring Data's paging/sorting abstractions — backends implement the
  `execute*` SPI (JPA and MongoDB are shipped; QueryDSL support is on the roadmap)
- **Registration:** lifecycle for discovering and registering DAL beans
- **Transactions & validation:** built-in cross-cutting concerns
- **Extension hooks:** lifecycle hooks for custom business logic
- **Registry:** developer lookup of DAL beans at runtime

## Key Public Types

### Annotations

| Annotation      | Target | Purpose                                                                                                                 |
|-----------------|--------|-------------------------------------------------------------------------------------------------------------------------|
| `@DalService`   | Class  | Declares a DAL service. Attributes: `name` (required, unique), `internal` (default false). Which operations it exposes on a channel is declared with that channel module's annotation (`@DalWeb` in `telaio-web`) |
| `@DalOperation` | Method | Tags a method with its `DalOperationType` (used internally on `Dal` interface methods)                                  |

### Core Contracts

| Type                | Purpose                                                                                                                                                      |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Dal<E, I>`         | CRUD contract: `E create(Map)`, `Page<E> read(FilterNode?, Pageable)`, `Optional<E> readOne(I)`, `Optional<E> update(I, Map)`, `void delete(I)`              |
| `AbstractDal<E, I>` | Base class implementing `Dal` with setter injection, lifecycle hooks, and transaction wrapping. Subclasses override `execute*()` methods and `defaultSort()` |
| `DalMetadata<E, I>` | Read-only entity type metadata (`entityClass`, `idClass`)                                                                                                    |

### Registration & Management

| Type                 | Purpose                                                                              |
|----------------------|--------------------------------------------------------------------------------------|
| `DalRegistry`        | Read-only interface: `getServiceByName(String)` → `Dal<?,?>`                         |
| `DalCatalog`         | Read-only view: `DalRegistry` + `DalDefinitionRegistry` (no registration); what interception providers receive |
| `DalManager`         | Composite registry (single entry point for querying and registering DALs)            |
| `DalDefinitionEntry` | Identity snapshot: DAL name, concrete `Dal` class, internal flag                     |
| `DalOperationType`   | Enum: `CREATE`, `READ`, `READ_ONE`, `UPDATE`, `DELETE`                               |
| `DalChannel`         | Open value type naming an invocation channel (`DalChannel.of("rest")`, `DalChannel.IN_PROCESS`); not an enum, so a third-party channel plugs in without touching core |
| `DalChannelDefinition` | SPI, one bean per channel module: `channel()` and `exposedOperations(Class<?> dalClass)` — reads the module's own annotation |

### Lifecycle & Interception

| Type                            | Purpose                                                                                                                                                           |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DalInterceptorProvider`        | SPI for contributing channel-agnostic interceptors (audit, metrics)                                                                                               |
| `DalInterceptionContext`        | Record carrying the `DalDefinitionEntry` (`dalName()`, `dalBeanClass()` accessors), built with `DalInterceptionContext.of(entry)`, handed to each `DalInterceptorProvider` once per DAL bean at startup |
| `DalTransactionPolicy`          | SPI for transaction management per DAL                                                                                                                            |
| `PassThroughTransactionManager` | No-op `PlatformTransactionManager` fallback for backends with no real transaction manager available                                                               |

### Operation adapter assembly

Every channel (REST today) calls a DAL through a `DalOperationAdapter` rather than through the `Dal` bean, so that
the channel-level interceptors (exposure, denied-attempt audit, security and field-level RBAC) always apply. Since
3.0.0 the chain is assembled by `telaio-core`, package `com.paganbit.telaio.core.adapter`, **once per
(channel, DAL)**: for every `DalChannelDefinition` bean a channel module registers, and every non-internal DAL the
definition exposes at least one operation of. Exposure is enforced by core's own `DalExposureInterceptor`, always
outermost, from the definition's answer; the channel module only translates the resulting
`DalOperationNotExposedException` (REST: 405 + `Allow`, or 404):

| Type                            | Purpose                                                                                                                                                                                                                                              |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DelegatingDalOperationAdapter` | Base adapter backed by a `Dal`: `create`, `read`, `readOne`, `update`, `delete` delegate to the `Dal` and return the entity directly. An empty `readOne` throws `DalEntityNotFoundException`, the same failure `AbstractDal` raises on update and delete.            |
| `DalOperationAdapterAssembler`  | Runs once every singleton exists: for each channel definition and each non-internal DAL exposing something on it, wraps the base adapter with `DalExposureInterceptor` and then with the interceptors contributed by every `DalAdapterInterceptorProvider` bean (lowest order outermost) and publishes the proxy to the registry. Internal DALs get no adapter. |
| `DalOperationAdapterRegistry`   | Holds the assembled adapters by (channel, DAL name): `register(DalChannel, String, DalOperationAdapter)` and `get(DalChannel, String)` (throws `DalNotFoundException` when the DAL has no chain on that channel — unknown, internal or exposing nothing there). The default bean (`InMemoryDalOperationAdapterRegistry`) is registered `@ConditionalOnMissingBean`; first registration wins. |

`DalAdapterInterceptorProvider` is the SPI a module implements to join this chain (`telaio-audit` contributes the
denied-attempt audit, `telaio-security` authorization and RBAC); it receives a `DalAdapterContext` record
(`definition`, `channel`, `catalog`, built with `DalAdapterContext.of(...)`), so the same provider is consulted once
per channel and may read which channel it is on. The rule for choosing between the two SPIs: an interceptor whose
behavior depends on the channel belongs in the adapter chain; one that only observes what happened belongs at the
`Dal` level (`DalInterceptorProvider` above), which sees every channel and in-process calls alike.

Channel modules resolve adapters from `DalOperationAdapterRegistry` under their own channel; `telaio-web` asks for
`rest` on every REST call. On REST an empty `readOne` becomes a `404` whose `detail` is
`<Entity> was not found for id: [<id>]`, the same text update and delete already return. Assembly runs in
every application that includes `telaio-core`; without a channel module no `DalChannelDefinition` exists and the
registry stays empty. The cost is one proxy per (channel, non-internal DAL) at startup.

### Exceptions

| Exception                            | When                                                                                                                                                              |
|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DalEntityNotFoundException`         | Entity with given ID not found                                                                                                                                    |
| `DalEntityValidationException`       | Validation fails (includes field-level details)                                                                                                                   |
| `DalDefinitionException`             | DAL misconfiguration (e.g. duplicate name, a `@DalService` bean that is not a `Dal`, two `DalChannelDefinition` beans for the same channel)                      |
| `DalRegistryException`               | DAL lookup fails                                                                                                                                                  |
| `DalNotFoundException`               | No DAL registered under the requested name, or no adapter for it on the requested channel (subclass of `DalRegistryException`, → 404 on REST)                   |
| `DalOperationNotExposedException`    | A call reached an operation the DAL does not expose on the chain's channel; carries `dalName()`, `operation()`, `channel()` (REST translates it to 405 + `Allow`, or 404 when the URI serves nothing) |
| `DalInvalidFilterException`          | A well-formed `q=` filter the entity cannot honor: unknown or non-persistent field, unknown or backend-unsupported function (→ 400 `"Invalid filter expression"`) |
| `DalInvalidSortException`            | A `sort=` property the read cannot honor: unknown, non-persistent or non-orderable (→ 400 `"Invalid sort parameter"`)                                             |
| `DalFilterFieldNotReadableException` | Subclass of `DalInvalidFilterException` raised by `telaio-security` when a `q=` field exists but the principal may not read it (same 400; audited `DENIED`)       |
| `DalSortFieldNotReadableException`   | Subclass of `DalInvalidSortException` raised by `telaio-security` when a `sort=` key exists but the principal may not read it (same 400; audited `DENIED`)        |

### Validation and JSON path resolution

`TelaioCoreAutoConfiguration` registers three application-wide singletons, each `@ConditionalOnMissingBean` so an
application can replace it with its own bean of the same type:

| Bean                           | Type                        | Role                                                                                                                                                                   |
|--------------------------------|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `dalJsonPropertyPathResolver`  | `JsonPropertyPathResolver`  | Translates dot-notation property paths between Java and JSON wire names for any entity type (built on the application's `ObjectMapper`; caches per type)               |
| `dalJsonFieldNameSortRewriter` | `JsonFieldNameSortRewriter` | Rewrites caller-supplied `Sort` properties from wire names to Java names; unknown properties raise `DalInvalidSortException`                                           |
| `dalValidator`                 | `DalValidator`              | `validate(Object target, Class<?> type)` — runs Jakarta Bean Validation over the merged entity and reports field errors under their JSON names (`DefaultDalValidator`) |

Every `AbstractDal` requires the sort rewriter and the validator (injected through `setSortRewriter` /
`setDalValidator`), and `telaio-security`, `telaio-jpa`, `telaio-mongo` and `telaio-openapi` all consume the same path
resolver. Replacing the resolver therefore changes filter/sort name resolution, RBAC field-existence checks and OpenAPI
naming at once.

## How Developers Use It

### 1. Declare a DAL Service

Annotate a class extending `AbstractDal` (or more commonly, `JpaDal`) with `@DalService`:

```java

@DalService(name = "announcements")
public class AnnouncementDalService extends JpaDal<Announcement, Long> {
}
```

The framework automatically:

- Detects the `@DalService` bean (via `DalDefinitionBeanPostProcessor`) as the application context starts
- Extracts the generic type arguments `<E, I>`
- Registers it with the `DalManager` under the given name
- Wires dependencies (repository, entity manager, validator, etc.)

### 2. Expose Only Specific Operations (Optional)

Exposure is declared per channel, with the channel module's own annotation stacked on the `@DalService` class. For
REST that is `@DalWeb` from `telaio-web`:

```java

@DalService(name = "articles")
@DalWeb(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
public class ArticleDalService extends JpaDal<Article, Long> {
}
```

Articles are now read-only on REST; create/update/delete are rejected by core's exposure interceptor before reaching
the DAL. No `@DalWeb` means every operation; `@DalWeb(operations = {})` means the DAL is not on REST at all. The
`Dal` bean itself keeps every operation for in-process callers.

### 3. Make a DAL Internal (Optional)

To register a DAL with all cross-cutting features but hide it from REST and OpenAPI:

```java

@DalService(name = "app-settings", internal = true)
public class AppSettingDalService extends JpaDal<AppSetting, String> {
}
```

In-process code can still call this DAL (via `DalRegistry`); it is not exposed remotely.

### 4. Override Lifecycle Hooks (Optional)

Customize behavior via extension hooks in `AbstractDal`:

```java

@DalService(name = "products")
public class ProductDalService extends JpaDal<Product, Long> {

    @Override
    protected void finalizeBeforeCreate(Product product) {
        // Recompute derived fields before persistence
        recomputeMargin(product);
    }

    @Override
    protected void finalizeAfterCreate(Product product) {
        // Record audit trail or side-effects after commit
        recordPrice(product, "INITIAL");
    }

    @Override
    protected @Nullable FilterNode defaultFilter() {
        // Inject a baseline filter into every read
        return null; // No default filter
    }
}
```

Available hooks:

- `finalizeBeforeCreate/Update` — Modify the entity inside the operation transaction, after validation and just before
  persistence (changes made here are **not** re-validated)
- `finalizeAfterCreate/Update/Read/ReadOne` — Side-effects after the operation
- `finalizeBeforeDelete/AfterDelete` — Hooks around deletion (receive the entity id)
- `defaultSort()` — Default sort order when none is requested
- `validateSortProperty(String)` — Called for every caller-supplied sort property after it has been rewritten to its
  Java name; a backend rejects properties it cannot order by with `DalInvalidSortException` (JPA and Mongo do this
  against their metamodel / mapping context). An unknown property reaches this hook even on an RBAC-guarded read: the
  security layer only rejects fields that exist and are hidden
- `defaultFilter()` — Implicit filter AND-combined with the request's `q` parameter and enforced on every operation
  addressing existing rows (list and by-id reads, updates, deletes — a hidden entity behaves like a missing one; create
  payloads are not scoped by it). The returned `FilterNode` can be built type-safely with the `@Filterable`-generated
  `<Entity>Filter` builder (Spring Filter's
  `typesafe` + `typesafe-processor` artifacts) or manually via the injected `filterBuilder` — see the
  [introspection module docs](./introspection.md) for when to use which

### 5. Validate Input Automatically

`AbstractDal` always validates input with Jakarta Bean Validation — `telaio-core` brings
`spring-boot-starter-validation`, and the `dalValidator` bean is a hard requirement of every DAL:

```java
// If Product has @NotNull(message="Price required") on the price field,
// a create() with null price raises DalEntityValidationException (400)
POST /dal/v1/

products {
    "name":"Widget"
}  // Missing price
→
DalEntityValidationException with
field details
```

### 6. Look Up a DAL at Runtime

Inject `DalRegistry` to retrieve a DAL by name:

```java

@Service
public class MyService {
    @Autowired
    private DalRegistry dalRegistry;

    public void doSomething() {
        Dal<?, ?> dal = dalRegistry.getServiceByName("products");
        // Use the DAL directly (in-process: no channel, so all operations are available)
    }
}
```

## Configuration

No module-level configuration. Configuration lives in dependent modules (web, security, audit, metrics).

## See Also

- [JPA Module](./jpa.md) — `JpaDal<E, I>` extends `AbstractDal` to delegate to Spring Data JPA
- [Security Module](./security.md) — `@DalSecurity` adds authorization and RBAC on top of `@DalService`
- [Audit Module](./audit.md) — `@DalAudit` adds operation logging
- [Metrics Module](./metrics.md) — `@DalMetrics` adds performance monitoring
- [Web Module](./web.md) — Exposes DALs as REST endpoints
- [Architecture](../architecture.md) — DAL lifecycle, adapter chain, interception
- [Getting Started](../getting-started.md) — The 3-file recipe
