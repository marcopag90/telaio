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

Instead of magic strings:

```java
filterBuilder.field("status").equal("PUBLISHED")
```

Use method references:

```java
import static io.paganbit.telaio.introspection.PropertyNameResolver.propertyName;

filterBuilder.field(propertyName(Article::getStatus))
    .equal(filterBuilder.input(ArticleStatus.PUBLISHED))
    .get()
```

This is used in the showcase's `ArticleDalService` to define the `defaultFilter()`:

```java
@Override
protected @Nullable FilterNode defaultFilter() {
    Authentication auth = DalSecurityContextHelper.getCurrentAuthentication();
    boolean isPowerUser = auth != null && auth.getAuthorities().stream()
        .anyMatch(a -> UserRole.DEVELOPER.equals(a) || UserRole.ADMIN.equals(a));
    if (isPowerUser) {
        return null;
    }
    return filterBuilder.field(propertyName(Article::getStatus))
        .equal(filterBuilder.input(ArticleStatus.PUBLISHED))
        .get();
}
```

## No Configuration

The introspection module has no configuration properties.

## See Also

- [Architecture](../architecture.md) — How introspection fits into the layered design
- [Core Module](./core.md) — Uses introspection for type resolution
- [Getting Started](../getting-started.md) — Using method references in filters
