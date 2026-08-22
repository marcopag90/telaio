# Telaio: Introspection Module

The introspection module provides low-level reflection utilities for type checking and property name resolution. It has
no framework dependencies and serves as a foundation for other modules.

## Purpose

Type introspection utilities enabling:

- Safe classification of simple vs. complex types
- Refactor-safe property name resolution via method references
- Cached type metadata lookups

## Key Public Types

| Type                         | Purpose                                                                                                                            |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `TypeUtil`                   | Utility methods for type checking (simple/complex classification)                                                                  |
| `DefaultSimpleTypePredicate` | Predicate identifying primitive-like types: `Boolean`, `Character`, `String`, `UUID`, `Number`, `Enum`, `Date`, `Temporal`, `Optional`, `Collection`, `Map` (plus primitives and arrays) |
| `PropertyNameResolver`       | Resolves property names from lambda method references with caching                                                                 |
| `PropertyRef`                | Represents a property reference for introspection                                                                                  |

## How Developers Use It

The introspection module is used internally by Telaio but is also exposed as a public utility. Most common use:
**refactor-safe property name resolution** in filter expressions and field lists.

There are two complementary ways to build a filter expression without magic strings — pick per situation:

1. **Generated type-safe builder** (Spring Filter's `typesafe` + `typesafe-processor` artifacts, both managed by the
   Telaio BOM) — annotate the entity with `@Filterable` and use the compile-time-generated `<Entity>Filter` class.
   Field names *and* operators are checked by the compiler. Prefer this when you own the entity and can annotate it.
   This is what the showcase's `ArticleDalService` uses for its `defaultFilter()`:

   ```java
   @Override
   protected @Nullable FilterNode defaultFilter() {
       Authentication auth = DalSecurityContextHelper.getCurrentAuthentication();
       boolean isPowerUser = auth != null && auth.getAuthorities().stream()
           .anyMatch(a -> UserRole.DEVELOPER.equals(a) || UserRole.ADMIN.equals(a));
       if (isPowerUser) {
           return null;
       }
       return ArticleFilter.where(filterBuilder)
           .status().equal(ArticleStatus.PUBLISHED)
           .build();
   }
   ```

2. **`propertyName(...)` + the plain `FilterBuilder`** — no code generation; the method reference resolves the
   property name, refactor-safe. Use it for one-off expressions, classes you cannot annotate, or dynamic field
   selection:

   ```java
   import static com.paganbit.telaio.introspection.PropertyNameResolver.propertyName;

   filterBuilder.field(propertyName(Article::getStatus))
       .equal(filterBuilder.input(ArticleStatus.PUBLISHED))
       .get()
   ```

Both produce a `FilterNode` carrying **Java property names**: fine anywhere server-side (the JPA converter handles
them), but when sending a `FilterNode` over the wire via the REST client the names must match the JSON field names.

## No Configuration

The introspection module has no configuration properties.

## See Also

- [Architecture](../architecture.md) — How introspection fits into the layered design
- [Core Module](./core.md) — Uses introspection for type resolution
- [Getting Started](../getting-started.md) — Using method references in filters
