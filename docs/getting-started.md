# Getting Started with Telaio

This guide walks you through setting up Telaio in a new Spring Boot project and creating your first REST API in about 5
minutes.

## Prerequisites

- **Java 21+** — Telaio is compiled to and distributed as Java 21 bytecode. (Only the optional `telaio-showcase` demo
  targets Java 25; that is not a requirement of the framework.)
- **Spring Boot 4.1.0+**
- **Maven 3.9+**

## Step 1: Add Dependencies

In your Spring Boot project's `pom.xml`, import the `telaio-bom` Bill of Materials in
`dependencyManagement`, then add the feature modules you need — without repeating the version on
each one. For a minimal setup:

```xml
<properties>
    <telaio.version>0.0.1-SNAPSHOT</telaio.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.paganbit</groupId>
            <artifactId>telaio-bom</artifactId>
            <version>${telaio.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Web API exposure -->
    <dependency>
        <groupId>io.paganbit</groupId>
        <artifactId>telaio-web</artifactId>
    </dependency>
    <!-- JPA/Hibernate implementation -->
    <dependency>
        <groupId>io.paganbit</groupId>
        <artifactId>telaio-jpa</artifactId>
    </dependency>
    <!-- Database driver (PostgreSQL shown; use MySQL, MariaDB, etc. as needed) -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

> **Note:** the BOM manages the Telaio modules and the third-party libraries Telaio integrates
> (Turkraft Spring Filter, SpringDoc); it does **not** override Spring Boot's own dependency
> management.

> **Using a single Telaio module only?** You can skip the BOM import and declare the version
> directly on that dependency (e.g. `telaio-core` with `<version>${telaio.version}</version>`).

### Optional Modules

Add these if you need the features (versions still come from the BOM):

```xml
<!-- Field-level access control -->
<dependency>
    <groupId>io.paganbit</groupId>
    <artifactId>telaio-security</artifactId>
</dependency>
<!-- Operation auditing -->
<dependency>
    <groupId>io.paganbit</groupId>
    <artifactId>telaio-audit</artifactId>
</dependency>
<!-- Performance monitoring -->
<dependency>
    <groupId>io.paganbit</groupId>
    <artifactId>telaio-metrics</artifactId>
</dependency>
<!-- Auto-generated OpenAPI docs -->
<dependency>
    <groupId>io.paganbit</groupId>
    <artifactId>telaio-openapi</artifactId>
</dependency>
<!-- Swagger UI for OpenAPI (optional but recommended). Version-less on purpose: the BOM also
     manages the third-party libraries Telaio integrates (Turkraft Spring Filter, SpringDoc),
     keeping them aligned with the versions Telaio is tested against. -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

## Step 2: Configure the Database

Create or update `application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypass
  jpa:
    hibernate:
      ddl-auto: update  # or 'create' for fresh start
    show-sql: false

telaio:
  web:
    openapi:
      enabled: true
  openapi:
    enabled: true
```

## Step 3: Create Your First Entity

Create a JPA entity with Bean Validation annotations:

```java
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Column(nullable = false)
    private String name;

    @NotNull(message = "Price is required")
    @Column(nullable = false)
    private Double price;

    private String description;

    private String category;

    @Column(nullable = false)
    private Boolean available = true;
}
```

## Step 4: Create a Repository

Create a Spring Data JPA repository extending `JpaDalRepository`:

```java
import io.paganbit.telaio.jpa.JpaDalRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaDalRepository<Product, Long> {
}
```

That's it — no custom methods needed.

## Step 5: Register the DAL Service

Create the service that exposes your entity as a REST API:

```java
import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.jpa.JpaDal;

@DalService(name = "products")
public class ProductDalService extends JpaDal<Product, Long> {
    // No methods needed — CRUD is inherited
}
```

That's all. Your REST API is now live.

## Step 6: Run the Application

Build and run your Spring Boot app:

```bash
mvn clean install
mvn spring-boot:run
```

Or run it directly from your IDE.

## Step 7: Test Your API

### Using curl

**Create a product:**

```bash
curl -X POST http://localhost:8080/dal/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop",
    "price": 999.99,
    "category": "electronics",
    "description": "High-performance laptop"
  }'
```

**Read products (paginated, no filter):**

```bash
curl "http://localhost:8080/dal/v1/products?page=0&size=10"
```

**Read products with filter:**

```bash
curl "http://localhost:8080/dal/v1/products?q=category:%27electronics%27&size=5"
```

The filter syntax is **Turkraft Spring Filter**. More examples:

- `name:'Laptop'` — exact match on name
- `price>500` — price greater than 500
- `category:'electronics' and price<1000` — combined conditions
- `available:true` — boolean field

**Read one product:**

```bash
curl http://localhost:8080/dal/v1/products/1
```

**Update a product (PATCH with RFC 7396 merge):**

```bash
curl -X PATCH http://localhost:8080/dal/v1/products/1 \
  -H "Content-Type: application/json" \
  -d '{"price": 1099.99}'
```

**Delete a product:**

```bash
curl -X DELETE http://localhost:8080/dal/v1/products/1
```

### Using Swagger UI

If you included the OpenAPI module, visit the Swagger UI:

```
http://localhost:8080/swagger-ui.html
```

Click the **"products"** endpoint group, expand any operation, and use the **"Try it out"** button to test
interactively.

## Next Steps

Now that you have a working API, explore:

1. **[Architecture Guide](architecture.md)** — Understand how Telaio processes requests
2. **[Security Guide](security-guide.md)** — Add authentication and role-based field filtering
3. **[Configuration Reference](configuration.md)** — Tune metrics, audit, and OpenAPI
4. **[REST API Reference](rest-api.md)** — Full endpoint and filter documentation
5. **[Observability Guide](observability.md)** — Enable audit logs and metrics collection
6. **[Module Docs](README.md#module-documentation)** — Deep-dive into any module

## Running the Showcase App

To see Telaio in action with multiple DALs, audit, security, metrics, and more, run the included showcase:

```bash
mvn -pl telaio-showcase spring-boot:run
```

The showcase starts PostgreSQL in a Docker container automatically (via `spring-boot-docker-compose`). Visit:

- **API base**: http://localhost:8080/dal/v1
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Metrics endpoint**: http://localhost:8080/actuator/telaiometrics
- **Health check**: http://localhost:8080/actuator/health

Test users (password equals username):

- `developer` — full access to all fields and operations
- `admin` — restricted field access (no cost data)
- `user` — read-only access to public fields

Use HTTP Basic Auth: `curl -u developer:developer http://localhost:8080/dal/v1/products`

## Common Issues

**"DAL service not found" (404)**

Ensure your `@DalService(name = "…")` matches the path: `POST /dal/v1/{dalName}`.

**"Validation failed" (400)**

Check Bean Validation annotations (`@NotBlank`, `@NotNull`, etc.) on your entity. The response includes a
`ProblemDetail` with field-level `errors`.

**"Hibernate: unknown entity"**

Register your entity with Hibernate. If using `@EnableJpaRepositories`, ensure your entity package is scanned, or add
`@EntityScan("com.example.entities")` to your `@SpringBootApplication`.

**"Cannot autowire JpaDalRepository"**

Ensure your repository extends `JpaDalRepository<E, I>` with the correct generic types matching your entity.

## Further Resources

- **GitHub**: [github.com/marcopag90/telaio](https://github.com/marcopag90/telaio)
- **Source**: Clone and explore `telaio-showcase` for complete examples
- **Tests**: Browse `telaio-web/src/test/…` for request/response examples
