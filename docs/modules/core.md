# Telaio: Core Module

The core module defines the DAL abstraction, registration, and bean lifecycle. Every other module depends on it
directly.

## Purpose

- **DAL contract:** five CRUD operations via dynamic property maps
- **Persistence-agnostic:** the contract uses only Spring Data's paging/sorting abstractions — backends implement the
  `execute*` SPI (JPA is the first; MongoDB and QueryDSL support are on the roadmap)
- **Registration:** lifecycle for discovering and registering DAL beans
- **Transactions & validation:** built-in cross-cutting concerns
- **Extension hooks:** lifecycle hooks for custom business logic
- **Registry:** developer lookup of DAL beans at runtime

## Key Public Types

### Annotations

| Annotation      | Target | Purpose                                                                                                                 |
|-----------------|--------|-------------------------------------------------------------------------------------------------------------------------|
| `@DalService`   | Class  | Declares a DAL service. Attributes: `name` (required, unique), `internal` (default false), `operations` (default all 5) |
| `@DalOperation` | Method | Tags a method with its `DalOperationType` (used internally on `Dal` interface methods)                                  |

### Core Contracts

| Type                | Purpose                                                                                                                                                      |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Dal<E, I>`         | CRUD contract: `E create(Map)`, `Page<E> read(FilterNode?, Pageable)`, `Optional<E> readOne(I)`, `Optional<E> update(I, Map)`, `void delete(I)`              |
| `AbstractDal<E, I>` | Base class implementing `Dal` with setter injection, lifecycle hooks, and transaction wrapping. Subclasses override `execute*()` methods and `defaultSort()` |
| `DalMetadata<E, I>` | Read-only entity type metadata (`entityClass`, `idClass`)                                                                                                    |

### Registration & Management

| Type                 | Purpose                                                                    |
|----------------------|----------------------------------------------------------------------------|
| `DalRegistry`        | Read-only interface: `getServiceByName(String)` → `Dal<?,?>`               |
| `DalManager`         | Composite registry (single entry point for querying registered DALs)       |
| `DalDefinitionEntry` | Metadata snapshot: DAL name, concrete `Dal` class, internal flag, exposed operations |
| `DalOperationType`   | Enum: `CREATE`, `READ`, `READ_ONE`, `UPDATE`, `DELETE`                     |

### Lifecycle & Interception

| Type                     | Purpose                                                             |
|--------------------------|---------------------------------------------------------------------|
| `DalInterceptorProvider` | SPI for contributing channel-agnostic interceptors (audit, metrics) |
| `DalInterceptionContext` | Record `(dalName, dalBeanClass)` handed to each `DalInterceptorProvider` once per DAL bean at startup, so the provider can decide which interceptor to contribute |
| `DalTransactionPolicy`   | SPI for transaction management per DAL                              |

### Exceptions

| Exception                      | When                                                                         |
|--------------------------------|------------------------------------------------------------------------------|
| `DalEntityNotFoundException`   | Entity with given ID not found                                               |
| `DalEntityValidationException` | Validation fails (includes field-level details)                              |
| `DalDefinitionException`       | DAL misconfiguration (e.g. duplicate name, invalid `@DalService` attributes) |
| `DalRegistryException`         | DAL lookup fails                                                             |
| `DalNotFoundException`         | No DAL registered under the requested name (subclass of `DalRegistryException`, → 404 at the web boundary) |

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

To restrict which CRUD operations are exposed on the REST boundary:

```java
@DalService(name = "articles", operations = {DalOperationType.READ, DalOperationType.READ_ONE})
public class ArticleDalService extends JpaDal<Article, Long> {
}
```

Articles are now read-only; create/update/delete are rejected at the boundary.

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

- `finalizeBeforeCreate/Update` — Modify the entity inside the operation transaction, after validation and just
  before persistence (changes made here are **not** re-validated)
- `finalizeAfterCreate/Update/Read/ReadOne` — Side-effects after the operation
- `finalizeBeforeDelete/AfterDelete` — Hooks around deletion (receive the entity id)
- `defaultSort()` — Default sort order when none is requested
- `defaultFilter()` — Implicit filter AND-combined with the request's `q` parameter

### 5. Validate Input Automatically

`AbstractDal` validates all input via Jakarta Bean Validation if present:

```java
// If Product has @NotNull(message="Price required") on the price field,
// a create() with null price raises DalEntityValidationException (400)
POST /dal/v1/products
{ "name": "Widget" }  // Missing price
→ DalEntityValidationException with field details
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
        // Use the DAL directly (no remote boundary, so all operations are available)
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
