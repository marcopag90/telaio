# Telaio: Showcase Module

The showcase is a reference Spring Boot application that demonstrates Telaio features in real code. It provides a suite
of example DALs, fixtures, and end-to-end tests. It is the only module that targets **Java 25** — the Telaio library
itself is compiled to and distributed as Java 21, so this is a property of the demo app, not of the framework.

## Purpose

- **Reference implementation:** Complete working examples of every Telaio feature
- **Developer playground:** Local PostgreSQL via docker-compose for interactive exploration
- **Test coverage:** Integration tests with Testcontainers PostgreSQL
- **Feature showcase:** DALs demonstrating security, audit, metrics, RBAC, lifecycle hooks, filtering

## Key Demo DALs

| DAL               | Highlights                                                                             | Key File                 |
|-------------------|----------------------------------------------------------------------------------------|--------------------------|
| **announcements** | Baseline: no security, no audit, metrics disabled                                      | `AnnouncementDalService` |
| **articles**      | Read-only (via `operations`), audit, default filter, role-based visibility             | `ArticleDalService`      |
| **products**      | Full: auth + property-based RBAC, lifecycle hooks, multi-entity transactions           | `ProductDalService`      |
| **employees**     | JsonView RBAC with hierarchical role visibility                                        | `EmployeeDalService`     |
| **bulletins**     | Custom auth adapter (admin writes), metrics disabled                                   | `BulletinDalService`     |
| **departments**   | Simple CRUD example                                                                    | `DepartmentDalService`   |
| **translations**  | Composite ID (`TranslationId`) — [Base64 `{id}` segment](../rest-api.md#composite-ids) | `TranslationDalService`  |
| **app-settings**  | Internal DAL (no REST/OpenAPI)                                                         | `AppSettingDalService`   |
| **feed**          | Append-only, `operations={CREATE,READ}`                                                | `FeedEntryDalService`    |
| **tickets**       | Calls another DAL over the remote `telaio-rest-client` (DAL-to-DAL round-trip)         | `SupportTicketDalService`|

## REST Client: DAL-to-DAL Round-Trip

The showcase depends on **`telaio-rest-client` at compile scope** and acts as a client of its own
DAL API. `SupportTicketDalService` demonstrates a DAL whose writes call another DAL through the
typed client:

- **create** → persists the ticket, then `POST`s an activity entry to the append-only `feed` DAL;
- **update** → applies the patch, then posts another `feed` entry.

The remote call is a genuine HTTP round-trip back to this same application, so it exercises the full
web + security chain exactly as an external client would. It is wired through the
`finalizeAfterCreate`/`finalizeAfterUpdate` lifecycle hooks, which run **inside the write's
transaction**: if the remote call fails the unchecked `DalClientException` propagates and the local
transaction **rolls back** (proven by `TicketFeedRollbackIT`).

This is a **dual-write, not a distributed transaction** — the guarantee is one-directional: only a
*failing* remote call rolls the local write back; a remote success followed by a local commit
failure leaves an orphaned feed entry. It is a teaching example, not a production pattern: the
synchronous self-call runs while the DB transaction is open (a real remote call must size connection
pools/timeouts, or publish out of band), and it carries a single static service credential with no
propagation of the caller's identity. See the `SupportTicketDalService` Javadoc for the full caveats.

The connection is configured in YAML and authenticated with an interceptor:

```yaml
telaio:
  rest-client:
    connections:
      self:                              # single connection ⇒ backs the primary TelaioClient bean
        base-url: http://localhost:8080
```

```java
// ShowcaseRestClientConfig: basic auth for the "self" connection, the idiomatic per-connection hook
@Bean
TelaioRestClientCustomizer selfConnectionBasicAuth(
        @Value("${telaio.showcase.self-client.username:user}") String username,
        @Value("${telaio.showcase.self-client.password:user}") String password) {
    return (connectionName, builder) -> {
        if ("self".equals(connectionName)) {
            builder.requestInterceptor(new BasicAuthenticationInterceptor(username, password));
        }
    };
}
```

`SupportTicketDalService` injects the primary `TelaioClient` and calls
`telaioClient.dal("feed", FeedActivity.class, Long.class).create(...)`. The round-trip is proven
end-to-end by `TicketFeedRoundTripIT`.

## Running the Showcase

### Prerequisites

- **JDK 25+** (showcase target is Java 25)
- **Docker** (for PostgreSQL)
- **Maven 3.9+**

### Build and Start

```bash
# Build all modules
mvn clean install

# Run the showcase application
mvn -pl telaio-showcase spring-boot:run
```

The application starts on `http://localhost:8080`.

### First Request

```bash
# Create an announcement
curl -X POST http://localhost:8080/dal/v1/announcements \
  -H "Content-Type: application/json" \
  -d '{"type":"INFO","title":"Hello Telaio","message":"Welcome!"}'

# List announcements
curl http://localhost:8080/dal/v1/announcements

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

## Database & Persistence

### Development

The showcase uses **PostgreSQL 17** started automatically via `spring-boot-docker-compose`:

```yaml
# compose.yaml
services:
  postgres:
    image: 'postgres:17'
    environment:
      POSTGRES_DB: postgres
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - '5432:5432'
    volumes:
      # Named volume keeps the data across application stop/restart and container recreation.
      - telaio_showcase_pgdata:/var/lib/postgresql/data

volumes:
  telaio_showcase_pgdata:
```

The named volume `telaio_showcase_pgdata` persists data across restarts.

**Schema:** Hibernate auto-schema-update (see `application.yaml`):

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

**Seeding:** `DataInitializer` populates demo data **idempotently** on startup:

```java

@Component
public class DataInitializer implements CommandLineRunner {

    private final AnnouncementRepository announcementRepository;
    // ... other repositories, assigned in an explicit constructor

    @Override
    public void run(String @NonNull ... args) {
        seedArticles();
        seedProducts();
        seedAnnouncements();
        // ... seed other entities
    }

    private void seedBulletins() {
        if (bulletinRepository.count() > 0) {
            return;  // Each seed* method is guarded, so restarts don't duplicate data
        }
        // ... build and save demo rows
    }
}
```

### Testing

Tests use **Testcontainers** to spin up a fresh PostgreSQL 17 container. All integration tests extend a shared base
class:

```java

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, AuditCaptureTestConfig.class})
abstract class AbstractShowcaseIT {
    // Boots the whole app on a random port against a real PostgreSQL (Testcontainers,
    // wired via @ServiceConnection in TestcontainersConfiguration) and drives it over
    // genuine HTTP with TestRestTemplate — tests run against PostgreSQL, not H2
}
```

Docker is required for tests; see `TestcontainersConfiguration` (which declares the `@ServiceConnection` PostgreSQL
container).

## Security

### Users and Roles

Configured in `SecurityConfiguration` with in-memory authentication:

| Username    | Password    | Roles       |
|-------------|-------------|-------------|
| `developer` | `developer` | `DEVELOPER` |
| `admin`     | `admin`     | `ADMIN`     |
| `user`      | `user`      | `USER`      |

Authentication: HTTP Basic only.

```bash
# HTTP Basic
curl -u developer:developer http://localhost:8080/dal/v1/products
```

### Authorization Examples

**Articles (read-only for non-power-users):**

- `developer` / `admin`: See all (DRAFT, PUBLISHED, ARCHIVED)
- `user`: See only PUBLISHED articles (implicit filter in `defaultFilter()`)

**Products (write restricted):**

- `developer` / `admin`: Full CRUD
- `user`: Read-only

**Employees (field visibility by role):**

- `developer`: See all fields
- `admin`: See all except `internalNotes`
- `user`: See only basic fields

## Configuration

### application.yaml Highlights

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        show_sql: true
        format_sql: true

management:
  endpoints:
    web:
      exposure:
        include: health, telaiometrics

telaio:
  rest-client:
    connections:
      self:                       # the app is a client of its own DAL API (DAL-to-DAL round-trip)
        base-url: http://localhost:8080
  web:
    openapi:
      enabled: true
  metrics:
    enabled: true
    bucket-duration: 10s
    flush-interval: 10s
    jdbc:
      enabled: true
      initialize-schema: always
      table-name: telaio_metrics_bucket
      retention: P1D
      cleanup-interval: PT1H
  openapi:
    enabled: true
    include-examples: false
    tag-per-dal: true
```

## Structure

```
telaio-showcase/
├── compose.yaml
├── src/main/java/com/paganbit/telaio/showcase/
│   ├── TelaioShowcaseApplication.java
│   ├── DataInitializer.java
│   ├── config/
│   │   ├── JacksonConfiguration.java
│   │   ├── JpaConfiguration.java
│   │   ├── SecurityConfiguration.java
│   │   ├── ShowcaseRestClientConfig.java
│   │   └── SwaggerConfiguration.java
│   ├── dal/
│   │   ├── announcement/
│   │   ├── article/
│   │   ├── product/
│   │   ├── employee/
│   │   ├── bulletin/
│   │   ├── department/
│   │   ├── translation/
│   │   ├── setting/
│   │   ├── feed/
│   │   └── ticket/       (SupportTicketDalService — DAL-to-DAL round-trip via the REST client)
│   └── role/
│       └── UserRole.java
├── src/main/resources/
│   └── application.yaml
├── src/test/java/
│   ├── com/paganbit/telaio/showcase/
│   │   ├── TelaioShowcaseApplicationTests.java
│   │   ├── TestcontainersConfiguration.java
│   │   └── it/
│   │       ├── AbstractShowcaseIT.java (shared base class)
│   │       └── *IT.java (integration tests, e.g. ProductRbacHooksIT.java)
│   └── resources/
│       └── application-test.yaml (disables docker-compose)
└── pom.xml
```

## End-to-End Tests

Integration tests live in the `it/` subpackage, are suffixed with `*IT.java`, and extend `AbstractShowcaseIT` (which
provides `TestRestTemplate`-based helpers such as `list`, `create`, `patch`, `delete`, and `body`). Excerpt from the
real `ProductRbacHooksIT`:

```java
class ProductRbacHooksIT extends AbstractShowcaseIT {

    private static final String DAL = "products";

    @Test
    void userCanReadButCannotWrite() {
        assertThat(list(USER, DAL, "size=5").getStatusCode())
            .as("read is open to everyone").isEqualTo(HttpStatus.OK);

        ResponseEntity<String> create = create(USER, DAL, body(productPayload("Nope", "10.00", "5.00", "it-deny")));
        assertThat(create.getStatusCode()).as("USER create denied").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch(USER, DAL, 1L, body(Map.of("price", new BigDecimal("1.00")))).getStatusCode())
            .as("USER update denied").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete(USER, DAL, 1L).getStatusCode())
            .as("USER delete denied").isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

Run tests:

```bash
mvn -pl telaio-showcase test
```

Tests run against a containerized PostgreSQL, not H2.

## See Also

- [Getting Started](../getting-started.md) — Quick start with the announcement example
- [Security Guide](../security-guide.md) — Product and Employee examples explained
- [REST API Guide](../rest-api.md) — Filtering examples
- [Architecture](../architecture.md) — How all pieces fit together
