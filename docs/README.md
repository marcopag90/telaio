# Telaio Developer Guide

This is the comprehensive developer guide for **Telaio**, a Spring Boot framework that transforms an entity and
repository into a secured, audited, and monitored REST API with zero boilerplate. The DAL abstraction is
persistence-agnostic (built on Spring Data); JPA/Hibernate is the first shipped backend.

## Quick Links

### Core Guides

- **[Getting Started](getting-started.md)** — Build your first API in 5 minutes
- **[Architecture](architecture.md)** — Understand the design, module layering, and request flow
- **[REST API Reference](rest-api.md)** — Endpoint signatures, filter syntax, and error codes
- **[Security Guide](security-guide.md)** — Authentication, authorization, RBAC, and exposure control
- **[Configuration Reference](configuration.md)** — All `telaio.*` properties and their defaults
- **[Observability](observability.md)** — Audit, metrics, actuator endpoints, and integration

### Module Documentation

| Module            | Purpose                                             | Start Here                                                |
|-------------------|-----------------------------------------------------|-----------------------------------------------------------|
| **introspection** | Type introspection and reflection utilities         | [modules/introspection.md](modules/introspection.md) |
| **core**          | DAL abstraction, CRUD contracts, Spring integration | [modules/core.md](modules/core.md)                   |
| **jpa**           | JPA/Hibernate-backed DAL implementation             | [modules/jpa.md](modules/jpa.md)                     |
| **security**      | Authentication, RBAC, field-level access control    | [modules/security.md](modules/security.md)           |
| **audit**         | Operation auditing with flexible event stores       | [modules/audit.md](modules/audit.md)                 |
| **metrics**       | Performance monitoring and usage statistics         | [modules/metrics.md](modules/metrics.md)             |
| **web**           | Dynamic REST API and endpoint exposure              | [modules/web.md](modules/web.md)                     |
| **openapi**       | Per-DAL OpenAPI documentation generation            | [modules/openapi.md](modules/openapi.md)             |
| **showcase**      | Reference SaaS admin app demonstrating all features | [modules/showcase.md](modules/showcase.md)           |

## What is Telaio?

Telaio is a Spring Boot framework for building REST APIs from your entities without writing controllers or DTOs. You
declare an entity and repository, annotate a service with `@DalService`, and Telaio generates a fully functional CRUD
REST API with:

- **Dynamic REST endpoints** under `/dal/v1/{dalName}`
- **Built-in validation** from `@Valid` annotations
- **Field-level RBAC** via pluggable adapters
- **Opt-in audit** of all DAL operations
- **On-by-default metrics** for performance monitoring
- **Automatic OpenAPI documentation** per DAL
- **Transaction management** and property merging
- **Per-operation exposure control** (hide operations structurally or deny conditionally)

No controllers. No DTOs. No boilerplate.

## The Three-File Recipe

Declare a JPA entity:

```java
@Entity
@Table(name = "announcements")
public class Announcement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String title;

    @NotBlank
    private String message;
}
```

Create a repository:

```java
public interface AnnouncementRepository
    extends JpaDalRepository<Announcement, Long> {
}
```

Register the DAL service:

```java
@DalService(name = "announcements")
public class AnnouncementDalService extends JpaDal<Announcement, Long> {
}
```

Your REST API is now live:

```bash
# Create
curl -X POST http://localhost:8080/dal/v1/announcements \
  -H "Content-Type: application/json" \
  -d '{"title": "Welcome", "message": "Hello, world!"}'

# Read (paginated)
curl http://localhost:8080/dal/v1/announcements

# Read one
curl http://localhost:8080/dal/v1/announcements/1

# Update (PATCH with RFC 7396 merge)
curl -X PATCH http://localhost:8080/dal/v1/announcements/1 \
  -H "Content-Type: application/json" \
  -d '{"message": "Updated message"}'

# Delete
curl -X DELETE http://localhost:8080/dal/v1/announcements/1
```

## Key Concepts at a Glance

**Entity as Hub**: Your entity is returned directly from the API — there is no DTO layer. RBAC adapters filter
fields on input and output to respect role-based visibility.

**Persistence-Agnostic DAL**: the `Dal` contract depends only on Spring Data abstractions; backends implement the
`execute*` SPI. JPA is the first implementation — MongoDB, QueryDSL and a reactive exposure are on the
[roadmap](../README.md#roadmap).

**No `@ComponentScan` in Autoconfiguration**: Every bean is registered explicitly via `@Bean` or `@Import`, keeping
autoconfigurations self-contained and testable.

**Channel-Agnostic Interception**: Audit and metrics apply to every invocation channel (REST, messaging, programmatic)
via the core `DalInterceptorProvider` SPI, separate from the web adapter chain.

**Pluggable Security**: `DalAuthAdapter` controls operation-level authorization; `DalRbacAdapter` controls field
visibility. Ship with `PropertyBased` and `JsonView` implementations, or provide your own.

**Boundary Control**: `@DalService(internal = true)` hides a DAL entirely. `@DalService(operations = {…})` exposes only
specific operations (the rest return 404/405).

## Module Dependency Graph

```
telaio-introspection (foundation)
    ↓
telaio-core (DAL abstraction)
    ├→ telaio-security (authz, RBAC)
    ├→ telaio-audit (operation logging)
    ├→ telaio-metrics (performance monitoring)
    ├→ telaio-web (REST endpoints)
    │    ↓
    │  telaio-openapi (auto-generated docs)
    └→ telaio-jpa (first backend implementation, Spring Data JPA)

All modules → telaio-showcase (demo app)
```

## Getting Help

- **Examples**: Browse `telaio-showcase` for complete, working DAL implementations
- **Tests**: The module test suites (`src/test/java/…/DalRestApiV1ControllerTest.java`, etc.) demonstrate expected
  behavior
- **GitHub**: Report issues or ask questions at [github.com/marcopag90/telaio](https://github.com/marcopag90/telaio)

## Next Steps

1. **[Getting Started](getting-started.md)** walks you through dependency setup and running the showcase
2. **[Architecture](architecture.md)** explains the design in depth with Mermaid diagrams
3. **[REST API Reference](rest-api.md)** documents every endpoint and parameter
4. **[Security Guide](security-guide.md)** shows how to add authentication and RBAC
5. **[Configuration Reference](configuration.md)** lists all properties
6. **[Observability](observability.md)** covers audit and metrics
