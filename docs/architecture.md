# Architecture Guide

This guide explains how Telaio is designed, how its modules interact, and how it processes REST requests from arrival to
response.

## Core Design Principles

**Entity as Hub**: Your JPA entity is the single source of truth. It flows through the entire request pipeline
unchanged — no conversion to DTOs, no shadow copies. Security adapters filter it on the way in and out, but the entity
itself remains central.

**No `@ComponentScan`**: Every Telaio autoconfiguration registers its beans explicitly via `@Bean` methods or `@Import`
annotations. This keeps autoconfigurations self-contained, testable, and free of hidden dependencies.

**Channel-Agnostic Interception**: Concerns like audit and metrics apply to every invocation channel (REST, messaging,
in-process calls) via the core's `DalInterceptorProvider` SPI. This keeps cross-cutting logic separate from the web
boundary.

**Boundary Control**: Exposure is a boundary concern only. A DAL can be hidden entirely (`internal = true`) or
restricted to specific operations (`operations = {…}`). The underlying `Dal` bean retains all methods, so trusted
in-process code is never affected.

**Persistence-Agnostic Core**: the `Dal` contract and `AbstractDal` depend only on Spring Data's paging/sorting
abstractions (`Page`, `Pageable`, `Sort`) — no JPA types. The abstract `execute*` methods are the persistence SPI a
backend implements: `telaio-jpa` is the first implementation, and additional backends (e.g. MongoDB, QueryDSL-based
querying) can plug into the same contract without touching core, security, audit, metrics, or the web boundary.

## Module Dependency Graph

```mermaid
graph TD
    A["telaio-introspection<br/>(reflection utilities)"]
    A --> B["telaio-core<br/>(DAL contracts)"]
    B --> C["telaio-security<br/>(auth + RBAC)"]
    B --> D["telaio-audit<br/>(operation logging)"]
    B --> E["telaio-metrics<br/>(performance)"]
    B --> F["telaio-web<br/>(REST endpoints)"]
    B --> G["telaio-jpa<br/>(first backend impl)"]
    F --> H["telaio-openapi<br/>(auto docs)"]
    C --> I["telaio-showcase<br/>(demo app)"]
    D --> I
    E --> I
    F --> I
    G --> I
    H --> I
```

**Layering explanation**:

1. **introspection**: Type introspection, property name resolution. No Telaio dependencies.
2. **core**: DAL abstraction (`Dal<E,I>`), CRUD contracts, bean registration, `DalManager`. Foundation for all modules.
3. **security**, **audit**, **metrics**: Cross-cutting adapters and interceptors. Depend on core only.
4. **web**: Dynamic REST routing (`DalRestApiV1Controller`). Depends on core.
5. **openapi**: Auto-generates OpenAPI specs. Depends on web (integrates with the REST controller).
6. **jpa**: JPA/Hibernate implementation of `AbstractDal` — the first backend of the persistence-agnostic contract
   (built on Spring Data JPA). Depends on core. Future backends (e.g. MongoDB) plug into the same `execute*` SPI.
7. **showcase**: Complete SaaS app demonstrating all modules.

## DAL Lifecycle

When you declare a DAL service, it goes through this sequence:

```mermaid
sequenceDiagram
    participant Code as Your Code
    participant Spring as Spring Context
    participant PostProc as DalDefinitionBeanPostProcessor
    participant Manager as DalManager
    participant Assembler as WebDalOperationAdapterAssembler
    participant WebReg as WebDalOperationAdapterRegistry
    participant Controller as DalRestApiV1Controller
    Code ->> Spring: @DalService public class ProductDalService extends JpaDal
    Spring ->> PostProc: Detect @DalService
    PostProc ->> Manager: Register DAL with name "products"
    Manager ->> Manager: Store definition (name, operations, internal, etc.)
    Note over Assembler: At startup
    Assembler ->> WebReg: Assemble web adapters (if not internal)
    Note over Controller: At request time
    Client ->> Controller: POST /dal/v1/products { name, price, ... }
    Controller ->> WebReg: Lookup "products" adapter
    WebReg ->> Controller: Return WebDalOperationAdapter
    Controller ->> WebReg: Invoke adapter.create(input)
    Note over WebReg: Adapter chain executes
    WebReg ->> WebReg: 1. WebDalExposureInterceptor (check if op exposed)
    WebReg ->> WebReg: 2. DalAuditDeniedInterceptor (record if denied)
    WebReg ->> WebReg: 3. DalSecurityInterceptor (authz + RBAC field filtering)
    WebReg ->> WebReg: 4. WebDalOperationAdapter (call Dal.create)
    WebReg ->> Manager: Get Dal bean
    Manager ->> Code: Dal.create(map)
    Code ->> Code: Validate input, persist
    Code ->> WebReg: Return entity
    WebReg ->> Controller: Return entity
    Controller ->> Client: 201 { id, name, price, ... }
```

**Key points**:

1. **Registration** happens once at startup via `DalDefinitionBeanPostProcessor` and `DalFactoryPostProcessor`; the
   web adapters are assembled separately by `WebDalOperationAdapterAssembler` (in `telaio-web`).
2. **Exposure check** (step 1) returns 404 or 405 for non-exposed operations before reaching the DAL.
3. **Audit denied** (step 2) records failed authorization attempts.
4. **Security** (step 3) runs the `DalAuthAdapter` to check if the principal can perform the operation, then applies
   the `DalRbacAdapter` to filter input/output fields by role — both happen inside the single
   `DalSecurityInterceptor` (RBAC is not a separate interceptor in the chain).
5. **Web operation** (step 4) calls the underlying `Dal` method with the filtered input.

## Per-Request Adapter Chain (REST Boundary)

When a REST request arrives at `DalRestApiV1Controller`, it flows through the **web adapter chain**. This chain is
responsible for exposure control, security checks, and RBAC filtering. Note that RBAC is not a separate interceptor:
the `DalSecurityInterceptor` first checks authorization via the `DalAuthAdapter`, then applies the `DalRbacAdapter`
to filter fields — the diagram shows them as distinct steps only to make the logical order visible:

```mermaid
graph TD
    A["HTTP Request<br/>POST /dal/v1/products<br/>{ name, price, costPrice }"] -->|1| B["WebDalExposureInterceptor<br/>(outermost)"]
    B -->|Check: is CREATE<br/>exposed?| C{Operation<br/>Exposed?}
    C -->|No| D["404 or 405<br/>+ Allow header"]
    C -->|Yes| E["DalAuditDeniedInterceptor"]
    D -->|Response| Z["Client"]
    E -->|Record if<br/>later denied| F["DalSecurityInterceptor"]
    F -->|Check: principal<br/>authorized?| G{Authorized?}
    G -->|No<br/>Access Denied| H["403<br/>ProblemDetail<br/>generic body"]
    H -->|Response| Z
    G -->|Yes| I["DalRbacAdapter<br/>(field filtering, applied by<br/>DalSecurityInterceptor)"]
    I -->|Filter input<br/>by role| J["WebDalOperationAdapter<br/>(calls Dal method)"]
    J -->|Invoke<br/>Dal.create input| K["Dal Bean"]
    K -->|Business logic:<br/>validate, merge,<br/>persist| L["Entity"]
    J -->|Filter output<br/>by role| M["Filtered Entity"]
    M -->|Response| Z
```

**Exit points (errors return `ProblemDetail`):**

- **404 / 405** (Exposure): Operation not on the exposed list. 405 includes `Allow: POST,GET,…` header. 404 if no
  operations are exposed.
- **403** (Security): `DalAuthAdapter` denied the principal. Body is generic (no detail leaks per OWASP).
- **400** (Validation): Entity validation failed. Body includes field-level `errors` extension.
- **404** (Not Found): Entity with the given ID doesn't exist.
- **500** (Server Error): Unexpected exception. Logged but never leaked to the client.

**Success path**:

- After the chain, the filtered entity is returned to the client (201 for CREATE, 200/204 for UPDATE, etc.).

## Channel-Agnostic Dal Interception

Independent of the web chain, the core `DalInterceptionBeanPostProcessor` wraps every `Dal` bean with interceptors from
`DalInterceptorProvider` beans. This path applies to **all channels** — REST, messaging, direct calls:

```mermaid
graph TD
    A["Invocation Channel<br/>(REST, messaging,<br/>or programmatic)"] -->|Any channel| B["DalInterceptorProvider Chain"]
    B -->|1. AUDIT_PRECEDENCE<br/>outermost| C["DalAuditInterceptor"]
    C -->|Record START| D["Invoke next"]
    D -->|2. METRICS_PRECEDENCE<br/>= AUDIT + 1000| E["DalMetricsInterceptor"]
    E -->|Time the op| F["Invoke next"]
    F -->|Call the actual| G["Dal Method<br/>create/read/update/etc"]
    G -->|Success or error| H["Audit records<br/>SUCCESS/ERROR"]
    H -->|Metrics recorded| I["Result returned<br/>to caller"]
```

**Key insight**: Audit and metrics work with `telaio-core` alone, independent of the REST module. If you call a `Dal`
bean directly from business logic, audit and metrics still apply.

**Precedence**:

- `AUDIT_PRECEDENCE`: Outermost. Sees and records every invocation.
- `METRICS_PRECEDENCE = AUDIT_PRECEDENCE + 1000`: Just inside audit. Times the actual operation.

## Validation and Transaction Flow

When a REST request reaches the `Dal` bean, the following happens:

```mermaid
graph TD
    A["WebDalOperationAdapter<br/>receives input Map"] -->|1: UPDATE only| B["Fetch existing entity<br/>(executeReadOne), then<br/>DalPropertyMerger"]
    B -->|Merge partial<br/>patch into<br/>fetched entity| C["Entity with<br/>merged values"]
    C -->|2| D["DalValidator<br/>(Bean Validation)"]
    D -->|Validate against<br/>@NotNull, @NotBlank,<br/>etc. constraints| E{Valid?}
    E -->|No| F["DalEntityValidationException<br/>→ 400"]
    E -->|Yes| G["DalTransactionPolicy<br/>(transactional)"]
    G -->|Wrap in<br/>Spring transaction| H["AbstractDal.execute()"]
    H -->|Call business<br/>logic override<br/>if present| I["executeCreate()/<br/>executeUpdate()"]
    I -->|Persist to DB| J["JPA repository"]
    J -->|COMMIT on success<br/>ROLLBACK on error| K["Entity in DB"]
    K -->|Return to<br/>adapter chain| L["Response"]
```

**Transactional boundaries**:

- Each DAL operation is wrapped in a Spring `@Transactional` by `DalTransactionPolicy`.
- Validation happens before the transaction (fail fast at 400).
- Persistence happens inside the transaction.
- The transaction is committed before the response is returned to the client.

**Custom logic**:

You can override the `finalize*` hooks (`finalizeBeforeCreate`, `finalizeAfterCreate`, `finalizeBeforeUpdate`,
`finalizeAfterUpdate`, `finalizeAfterRead`, `finalizeAfterReadOne`) on `AbstractDal` to add business rules, side
effects, or enrichment. The `executeCreate()`/`executeRead()` methods are abstract persistence operations implemented by
`JpaDal`, not customization hooks.

## Security Model

Telaio employs a **layered security model** with four distinct concerns:

```mermaid
graph TD
    A["HTTP Request<br/>+ Principal"] -->|1| B["@DalSecurity(authAdapterClass)"]
    B -->|Defaults:<br/>Bare @DalSecurity → DenyAll<br/>Absent → PermitAll| C["DalAuthAdapter<br/>(operation-level)"]
    C -->|Check: Can principal<br/>create/read/update/delete?| D{Authorized?}
    D -->|No| E["403 Access Denied<br/>generic body"]
    E -->|Response| Z["Client"]
    D -->|Yes| F["DalRbacAdapter<br/>(field-level)"]
    F -->|Input: Filter fields<br/>the principal<br/>can write| G["Filtered Input"]
    G -->|Process| H["Entity in DB"]
    H -->|Output: Filter fields<br/>the principal<br/>can read| I["Filtered Entity"]
    I -->|Response| Z
    J["Structural Exposure<br/>@DalService operations={…}"] -->|vs| K["Conditional Authorization<br/>DalAuthAdapter"]
    J -->|Hides operations<br/>from API<br/>404/405<br/>no OpenAPI| L["Read-only<br/>or<br/>structural restriction"]
    K -->|Denies operations<br/>per principal<br/>403 in logs<br/>stays in OpenAPI| M["Identity-conditional<br/>authorization"]
```

**Four layers** (in order of checking):

1. **Exposure**: `@DalService(operations = {…})` — if the operation isn't in the list, return 404/405 immediately. This
   is structural: the operation never exists for anyone.
2. **Authentication**: Spring Security principals must be authenticated (handled by `SecurityConfiguration` in the
   showcase).
3. **Operation-Level Authorization** (`DalAuthAdapter`): Does the authenticated principal have permission for this
   operation? Defaults to `DenyAll` (bare `@DalSecurity`) or `PermitAll` (no annotation).
4. **Field-Level Access Control** (`DalRbacAdapter`): Which fields can the principal read and write? Applies after
   authorization passes.

**Defaults**:

- **With `@DalSecurity`** (bare): `DenyAllDalAuthAdapter` + `NoopDalRbacAdapter` → **secure by default**. You must
  explicitly grant access.
- **Without `@DalSecurity`**: `PermitAllDalAuthAdapter` + `NoopDalRbacAdapter` → **open by default**. Useful for public
  APIs.

## Configuration and Autoregistration

Telaio uses **explicit `@Bean` registration** in autoconfigurations, never `@ComponentScan`. This pattern keeps modules
self-contained and testable:

```mermaid
graph LR
    A["telaio-core<br/>TelaioCoreAutoConfiguration"] -->|Registers| B["DalManager<br/>DalPropertyMerger<br/>DalTransactionPolicy<br/>TelaioVersionProvider"]
    C["telaio-security<br/>TelaioSecurityAutoConfiguration"] -->|Registers| D["DalSecurityInterceptorProvider<br/>(creates DalSecurityInterceptor)"]
    E["telaio-audit<br/>TelaioAuditAutoConfiguration"] -->|Registers| F["DalAuditInterceptorProvider<br/>DalAuditEventStore"]
    G["telaio-metrics<br/>TelaioMetricsAutoConfiguration"] -->|Registers| H["DalMetricsInterceptorProvider<br/>DalMetricsStore<br/>DalMetricsFlushScheduler"]
    I["telaio-web<br/>TelaioWebAutoConfiguration"] -->|Registers via @Import| J["DalRestApiV1Controller<br/>WebDalOperationAdapterRegistry"]
    K["telaio-openapi<br/>TelaioOpenApiAutoConfiguration"] -->|Registers| L["DalOpenApiCustomizer<br/>DalPathsGenerator"]
```

**Why explicit registration?**

- **Testability**: Autoconfigurations can be tested with `ApplicationContextRunner` without
  `BeanDefinitionOverrideException`.
- **Clarity**: Every bean and its dependencies are explicit in the code, not hidden by classpath scanning.
- **Modularity**: Modules can be included or excluded cleanly. No transitive scan side effects.

## Error Handling

All errors are mapped to RFC 9457 `ProblemDetail` with `application/problem+json` content type:

```mermaid
graph TD
    A["Exception thrown<br/>in DAL or adapter"] -->|Caught by| B["TelaioWebExceptionHandler<br/>(Spring @ExceptionHandler)"]
    B -->|Categorize| C{Error Type}
    C -->|400<br/>Validation| D["DalEntityValidationException"]
    D -->|Response| E["ProblemDetail<br/>status: 400<br/>detail: Validation failed<br/>errors: field-level array"]
    C -->|403<br/>Access Denied| F["AccessDeniedException<br/>(Spring Security)"]
    F -->|Mapped by| G["TelaioAccessDeniedExceptionHandler"]
    G -->|Response| H["ProblemDetail<br/>status: 403<br/>title: Forbidden<br/>detail: (omitted)"]
    C -->|404<br/>Not Found| I["DalEntityNotFoundException<br/>DalResourceNotFoundException"]
    I -->|Response| J["ProblemDetail<br/>status: 404"]
    C -->|500<br/>Server Error| K["DalRegistryException"]
    K -->|Log at WARN| L["ProblemDetail<br/>status: 500<br/>detail: (omitted)<br/>logged internally"]
    M["Logging Policy"] -->|4xx, 403| N["DEBUG<br/>(client errors,<br/>don't flood logs)"]
    M -->|5xx| O["WARN with stack<br/>(server fault)"]
    E -->|Response| Z["Client"]
    H -->|Response| Z
    J -->|Response| Z
    L -->|Response| Z
```

**Logging policy**: 4xx errors are logged at DEBUG (avoid DoS-flooding production logs), but the durable trail is
**telaio-audit** (DENIED and ERROR events are always recorded). The handler maps `DalRegistryException` to a generic
500; other unexpected exceptions fall back to Spring's default error handling.

## Request Flow: Complete Example

Here's a complete walkthrough of a PATCH request to update a product:

```
1. Client sends:
   PATCH /dal/v1/products/1
   Authorization: Basic ZGV2ZWxvcGVyOmRldmVsb3Blcg==
   Content-Type: application/json
   { "price": 1299.99 }

2. DalRestApiV1Controller receives the request
   ↓

3. WebDalOperationAdapter.update() is called
   ↓

4. WebDalExposureInterceptor checks: Is UPDATE in @DalService(operations = {…})?
   If not → 404 or 405 (empty body)
   If yes → continue
   ↓

5. DalAuditDeniedInterceptor: standby (will record if later denied)
   ↓

6. DalSecurityInterceptor checks: Does principal "developer" have UPDATE authorization?
   If DalAuthAdapter.authorizeUpdate(auth, id) returns false → 403 + audit DENIED
   If true → continue
   ↓

7. DalSecurityInterceptor applies the DalRbacAdapter to the input:
   - Input is { "price": 1299.99 }
   - DEVELOPER role can write: name, price, etc.
   - Result: same (all fields are writable)
   ↓

8. WebDalOperationAdapter delegates to Dal.update(id, filteredInput)
   ↓

9. AbstractDal fetches the existing entity via executeReadOne(id),
   then DalPropertyMerger merges the patch into it:
   Product { id: 1, name: "Laptop", price: 999.99, … }
   + { "price": 1299.99 }
   = Product { id: 1, name: "Laptop", price: 1299.99, … }
   ↓

10. DalValidator: Check Bean Validation constraints. If @NotNull price and it's set → pass
    ↓

11. DalTransactionPolicy: Open transaction
    ↓

12. AbstractDal.executeUpdate(): Delegate to repository.save(entity)
    ↓

13. Hibernate: UPDATE products SET … (UPDATE statement)
    ↓

14. Transaction commits
    ↓

15. DalSecurityInterceptor applies the DalRbacAdapter to the output:
    - Output is the updated Product
    - DEVELOPER can read all fields
    - Result: same
    ↓

16. DalMetricsInterceptor records the timing (metrics are on by default).
    If the DAL were annotated @DalAudit (the showcase "products" DAL is not),
    DalAuditInterceptor would also record:
    SUCCESS, operation=UPDATE, principal=developer, durationMs=45
    ↓

17. Controller returns: 200 { id: 1, name: "Laptop", price: 1299.99, … }
```

## Summary

Telaio separates concerns across layers:

- **Core** (`telaio-core`): DAL abstraction, registration, CRUD contracts.
- **Boundary** (`telaio-web`): REST routing, exposure control, the adapter chain.
- **Cross-cutting** (`telaio-security`, `telaio-audit`, `telaio-metrics`): Interceptors and adapters plugged into both
  layers.
- **Persistence** (`telaio-jpa`): JPA-specific implementation.
- **Documentation** (`telaio-openapi`): Auto-generated specs.

This modular design lets you use Telaio in different contexts (REST, messaging, direct calls) while keeping concerns
isolated and composable.
