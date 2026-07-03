# Telaio: Security Module

The security module provides operation-level authorization and field-level RBAC (role-based access control) for DAL
operations. It requires Spring Security on the classpath.

## Purpose

- **Operation-level authorization:** `DalAuthAdapter` defines CRUD-level access rules
- **Field-level RBAC:** `DalRbacAdapter` filters input/output properties by role
- **Integration with Spring Security:** Works with `Authentication` and `GrantedAuthority`
- **Pluggable adapters:** Built-in strategies for common patterns

## Key Public Types

### Annotations

| Annotation     | Target | Purpose                                                                                                                                                                                                                                   |
|----------------|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@DalSecurity` | Class  | Configures the auth and RBAC adapters for a DAL. Attributes: `authAdapterClass`, `rbacAdapterClass`. Default: `@DalSecurity(DenyAllDalAuthAdapter, NoopDalRbacAdapter)`. **Omitting this annotation entirely = PermitAll + Noop (open).** |

### Authorization (Operation-Level)

| Type                      | Purpose                                                                                                                                                       |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DalAuthAdapter<I>`       | SPI: `boolean authorizeCreate/Read(Authentication)`, `boolean authorizeReadOne/Update/Delete(Authentication, I id)` — return `true` to allow, `false` to deny |
| `PermitAllDalAuthAdapter` | Built-in: allows all authenticated users for all operations                                                                                                   |
| `DenyAllDalAuthAdapter`   | Built-in: denies all users for all operations (default when `@DalSecurity` is present)                                                                        |

### RBAC (Field-Level)

| Type                          | Purpose                                                                                                                                                                                  |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DalRbacAdapter<T>`           | SPI: `Map<String,Object> filterInput(DalOperationType, Map<String,Object>, Authentication)` and `Object filterOutput(DalOperationType, T, Authentication)` (both `default` pass-through) |
| `NoopDalRbacAdapter`          | Built-in: no filtering (default when `@DalSecurity` is absent or omitted)                                                                                                                |
| `PropertyBasedDalRbacAdapter` | Built-in: abstract base class defining readable/writable fields per role via property name maps                                                                                          |
| `JsonViewDalRbacAdapter`      | Built-in: abstract base class delegating to Jackson `@JsonView` annotations on the entity                                                                                                |

### Support

| Type                       | Purpose                                                                                                            |
|----------------------------|--------------------------------------------------------------------------------------------------------------------|
| `DalSecurityContextHelper` | Utility: `getCurrentAuthentication()`, `getCurrentRequestAttributes()`                                             |
| `DalAccessDeniedException` | Exception raised when authorization fails (→ 403 HTTP response, customizable via `DalAccessDeniedMessageResolver`) |

## How Developers Use It

### Default Behavior

**No `@DalSecurity` annotation:**

```java
@DalService(name = "announcements")
public class AnnouncementDalService extends JpaDal<Announcement, Long> {
}
```

→ **PermitAll auth + Noop RBAC**: Any authenticated user can perform any operation; no fields are hidden.

**Bare `@DalSecurity` annotation:**

```java
@DalService(name = "bulletins")
@DalSecurity
public class BulletinDalService extends JpaDal<Bulletin, Long> {
}
```

→ **DenyAll auth + Noop RBAC**: All operations are denied (403) unless a custom auth adapter is supplied.

### Strategy 1: Operation-Level Authorization

Implement `DalAuthAdapter` to control which operations each role can perform:

```java
@Component
public class ProductAuthAdapter implements DalAuthAdapter<Long> {

    @Override
    public boolean authorizeCreate(Authentication authentication) {
        return hasAnyRole(authentication);
    }

    @Override
    public boolean authorizeRead(Authentication authentication) {
        return true;
    }

    @Override
    public boolean authorizeReadOne(Authentication authentication, Long id) {
        return true;
    }

    @Override
    public boolean authorizeUpdate(Authentication authentication, Long id) {
        return hasAnyRole(authentication);
    }

    @Override
    public boolean authorizeDelete(Authentication authentication, Long id) {
        return hasAnyRole(authentication);
    }

    private boolean hasAnyRole(Authentication auth) {
        for (UserRole role : new UserRole[]{UserRole.ADMIN, UserRole.DEVELOPER}) {
            if (auth.getAuthorities().contains(role)) {
                return true;
            }
        }
        return false;
    }
}
```

Then wire it:

```java
@DalService(name = "products")
@DalSecurity(authAdapterClass = ProductAuthAdapter.class)
public class ProductDalService extends JpaDal<Product, Long> {
}
```

### Strategy 2: Field-Level RBAC via Property Maps

Extend `PropertyBasedDalRbacAdapter` to define readable/writable fields per role:

```java
@Component
public class ProductRbacAdapter extends PropertyBasedDalRbacAdapter<Product> {

    @Override
    protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
        return Map.ofEntries(
            Map.entry(new SimpleGrantedAuthority("USER"),
                Set.of("id", "name", "price", "description")),
            Map.entry(new SimpleGrantedAuthority("DEVELOPER"),
                Set.of("id", "name", "price", "costPrice", "description", "internalSku", "marginPercentage")),
            Map.entry(new SimpleGrantedAuthority("ADMIN"),
                Set.of("id", "name", "price", "costPrice", "description", "internalSku", "marginPercentage"))
        );
    }

    @Override
    protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
        return Map.ofEntries(
            Map.entry(new SimpleGrantedAuthority("DEVELOPER"),
                Set.of("name", "price", "costPrice", "description", "internalSku")),
            Map.entry(new SimpleGrantedAuthority("ADMIN"),
                Set.of("name", "price", "costPrice", "description", "internalSku"))
        );
    }
}
```

Wire it:

```java
@DalService(name = "products")
@DalSecurity(
    authAdapterClass = ProductAuthAdapter.class,
    rbacAdapterClass = ProductRbacAdapter.class
)
public class ProductDalService extends JpaDal<Product, Long> {
}
```

When a USER requests `PATCH /dal/v1/products/1` with `{ "costPrice": 50 }`, the framework:

1. Calls `ProductAuthAdapter.authorizeUpdate()` — passes (everyone can call update)
2. Calls `ProductRbacAdapter.filterInput()` — strips `costPrice` (USER cannot write it)
3. Persists the product with only allowed fields modified

### Strategy 3: Field-Level RBAC via Jackson `@JsonView`

Use `@JsonView` annotations on the entity for hierarchical role-based visibility:

```java
public interface EmployeeView {
    interface User extends Department.PublicView {}
    interface Admin extends User {}
    interface Developer extends Admin {}
}

@Entity
public class Employee {
    @Id
    @JsonView(EmployeeView.User.class)
    private Long id;

    @JsonProperty("employeeName")
    @JsonView(EmployeeView.User.class)
    private String name;

    @JsonProperty("employeeEmail")
    @JsonView(EmployeeView.User.class)
    private String email;

    @JsonView(EmployeeView.Admin.class)
    private BigDecimal salary;

    @JsonView(EmployeeView.Developer.class)
    private String internalNotes;
}
```

Extend `JsonViewDalRbacAdapter` to map roles to views:

```java
@Component
public class EmployeeRbacAdapter extends JsonViewDalRbacAdapter<Employee> {

    @Override
    protected @Nullable Class<?> resolveView(DalOperationType operation, Authentication auth) {
        if (auth == null) return null; // Deny all

        Set<String> roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        if (roles.contains("DEVELOPER")) return EmployeeView.Developer.class;
        if (roles.contains("ADMIN")) return EmployeeView.Admin.class;
        if (roles.contains("USER")) return EmployeeView.User.class;
        return null; // Unknown role → deny
    }
}
```

Wire it:

```java
@DalService(name = "employees")
@DalSecurity(
    authAdapterClass = PermitAllDalAuthAdapter.class,
    rbacAdapterClass = EmployeeRbacAdapter.class
)
public class EmployeeDalService extends JpaDal<Employee, Long> {
}
```

Now responses include only the fields visible to the principal's role, and the view hierarchy automatically includes
parent fields.

## Configuration

No module-specific configuration. See also [Security Guide](../security-guide.md) for detailed strategy examples.

## See Also

- [Security Guide](../security-guide.md) — Deep dive into authorization patterns
- [REST API Guide](../rest-api.md) — How 403 errors are returned
- [Architecture](../architecture.md) — Security's place in the adapter chain
- [Core Module](./core.md) — The DAL contract
