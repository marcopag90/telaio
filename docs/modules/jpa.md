# Telaio: JPA Module

The JPA module is the **first backend implementation** of the persistence-agnostic DAL abstraction, built on Spring
Data JPA. It delegates CRUD operations to a Spring Data JPA repository and converts Turkraft filter expressions to JPA
`Specification` objects.

The core contract knows nothing about JPA — `Dal`/`AbstractDal` use only Spring Data's paging/sorting abstractions,
and a backend implements the `execute*` SPI. Additional backends (e.g. MongoDB, QueryDSL-based querying) are on the
roadmap and plug into the same contract.

## Purpose

- **Spring Data JPA integration:** Transparent delegation to your repository
- **Filter-to-JPA conversion:** Dynamic Turkraft filter queries → JPA `Specification`
- **Type-safe repository definitions:** Minimal boilerplate interface declarations
- **Setter-based injection:** Concrete DAL classes need no constructor in Spring
- **Metadata extraction:** Entity type and default sort order resolution

## Key Public Types

### Core DAL Implementation

| Type                     | Purpose                                                                                                                                                                                                       |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `JpaDal<E, I>`           | Extends `AbstractDal`, implements `JpaDalMetadata`. Delegates CRUD to `JpaDalRepository` and converts filters to JPA `Specification`. Setter-injects `repository`, `entityManager`, `specificationConverter`. |
| `JpaDalRepository<T, I>` | Spring Data `@NoRepositoryBean` interface extending `JpaRepositoryImplementation`. Developers write: `interface XRepository extends JpaDalRepository<X, Long> {}`. No custom methods needed.                  |
| `JpaDalMetadata<E, I>`   | Read-only: `JpaDalRepository<E,I> getRepository()` + `EntityType<E> getEntityType()`. Exposes the backing repository and JPA metamodel introspection.                                                         |

### Support

| Type                        | Purpose                                                                        |
|-----------------------------|--------------------------------------------------------------------------------|
| `EntityDefaultSortResolver` | Resolves default sort order from entity metadata (e.g. via custom annotations) |
| `ByIdSpecification`         | Specification for filtering by ID                                              |

### Configuration

| Type                         | Purpose                                                              |
|------------------------------|----------------------------------------------------------------------|
| `TelaioJpaAutoConfiguration` | Spring Boot autoconfiguration (conditional on `DataSource` and Turkraft's `FilterSpecificationConverter` being on the classpath) |

## How Developers Use It

### 1. Define a Repository Interface

No implementation needed — Spring Data JPA generates it:

```java
public interface AnnouncementRepository
    extends JpaDalRepository<Announcement, Long> {
}
```

Or use `@Repository` for a concrete bean:

```java
@Repository
public interface AnnouncementRepository
    extends JpaDalRepository<Announcement, Long> {
}
```

### 2. Define a JPA DAL Service

Extend `JpaDal` and annotate with `@DalService`. Spring's generic-aware autowiring supplies the repository:

```java
@DalService(name = "announcements")
public class AnnouncementDalService extends JpaDal<Announcement, Long> {
}
```

The framework:

- Autowires `AnnouncementRepository` into the `repository` setter
- Autowires `EntityManager` into the `entityManager` setter
- Autowires `FilterSpecificationConverter` into the `specificationConverter` setter
- Calls `afterPropertiesSet()` to extract the generic `<E, I>` and validate

**No constructor needed.** Setter injection keeps the class clean.

### 3. (Optional) Add Custom Methods to the Repository

If you need queries beyond CRUD:

```java
@Repository
public interface ProductRepository
    extends JpaDalRepository<Product, Long> {

    List<Product> findByPriceGreaterThan(BigDecimal price);
}
```

Then access them via the injected repository in your DAL:

```java
@DalService(name = "products")
public class ProductDalService extends JpaDal<Product, Long> {

    // repository is autowired into the parent class
    public List<Product> getExpensiveItems(BigDecimal threshold) {
        return repository.findByPriceGreaterThan(threshold);
    }
}
```

### 4. Override Lifecycle Hooks

```java
@DalService(name = "products")
public class ProductDalService extends JpaDal<Product, Long> {

    @Override
    protected void finalizeBeforeCreate(Product product) {
        // Computed fields, defaults, etc.
        product.setMarginPercentage(computeMargin(product));
    }

    @Override
    protected void finalizeAfterCreate(Product product) {
        // Side-effects (e.g., write audit log, trigger event)
        recordPrice(product, "INITIAL");
    }

    @Override
    protected @Nullable FilterNode defaultFilter() {
        // Implicit row-level filter applied to every read
        return null;
    }

    @Override
    protected Sort defaultSort() {
        // Default sort when the caller doesn't specify one
        return Sort.by("id").descending();
    }
}
```

Available hooks (from `AbstractDal`):

- `finalizeBeforeCreate/Update(E)` — Modify the entity inside the operation transaction, after validation and just
  before persistence (changes made here are **not** re-validated)
- `finalizeAfterCreate/Update/Read/ReadOne(E)` — Side-effects after the operation
- `finalizeBeforeDelete/AfterDelete(I)` — Hooks around deletion (receive the entity id)
- `defaultSort()` → `Sort` — Default sort order
- `defaultFilter()` → `FilterNode?` — Implicit baseline filter (enforced on reads, updates and deletes)

### 5. Use Filtering

Clients send Turkraft filter expressions in the `q` parameter:

```bash
curl 'http://localhost:8080/dal/v1/announcements?q=type:%27URGENT%27'
```

`JpaDal` converts this to a JPA `Specification` internally and passes it to the repository.

### 6. Validate Entities

If Jakarta Bean Validation is on the classpath, `AbstractDal` validates all input:

```java
@Entity
public class Product {
    @NotBlank
    private String name;

    @Min(0)
    @DecimalMin("0.01")
    private BigDecimal price;
}
```

A create/update with invalid data raises `DalEntityValidationException` (400 HTTP response with field details).

## Example: The Product DAL

The showcase's `ProductDalService` demonstrates the full pattern:

```java
@DalService(name = "products")
@DalSecurity(
    authAdapterClass = ProductAuthAdapter.class,
    rbacAdapterClass = ProductRbacAdapter.class
)
@RequiredArgsConstructor
public class ProductDalService extends JpaDal<Product, Long> {

    private final ProductPriceHistoryRepository priceHistoryRepository;

    @Override
    protected void finalizeBeforeCreate(Product product) {
        recomputeMargin(product);
    }

    @Override
    protected void finalizeBeforeUpdate(Product product) {
        recomputeMargin(product);
    }

    @Override
    protected void finalizeAfterCreate(Product product) {
        computeProfit(product);
        recordPrice(product, "INITIAL");
    }

    @Override
    protected void finalizeAfterUpdate(Product product) {
        computeProfit(product);
        recordPrice(product, "UPDATE");
    }

    @Override
    protected void finalizeAfterRead(Product product) {
        computeProfit(product);
    }

    @Override
    protected void finalizeAfterReadOne(Product product) {
        computeProfit(product);
    }

    private void recomputeMargin(Product product) {
        BigDecimal price = product.getPrice();
        BigDecimal cost = product.getCostPrice();
        if (price.signum() > 0) {
            BigDecimal margin = price.subtract(cost)
                .divide(price, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
            product.setMarginPercentage(margin);
        }
    }

    private void computeProfit(Product product) {
        BigDecimal price = product.getPrice();
        BigDecimal cost = product.getCostPrice();
        product.setProfit(price.subtract(cost));
    }

    private void recordPrice(Product product, String reason) {
        ProductPriceHistory entry = new ProductPriceHistory(
            product.getId(),
            product.getPrice(),
            reason,
            LocalDateTime.now(ZoneId.systemDefault())
        );
        priceHistoryRepository.save(entry);
    }
}
```

## Configuration

No module-specific configuration. See [Configuration Reference](../configuration.md) for framework-wide settings.

## See Also

- [Getting Started](../getting-started.md) — The 3-file recipe (entity + repository + `@DalService`)
- [Core Module](./core.md) — `AbstractDal` base class and lifecycle hooks
- [REST API Guide](../rest-api.md) — How filtering works at the HTTP boundary
- [Architecture](../architecture.md) — DAL lifecycle and transaction flow
