# Security Guide

This guide covers Telaio's layered security model: authentication, operation-level authorization, field-level RBAC, and
exposure control.

## Security Layers

Telaio enforces security at **four distinct layers**:

```
1. Exposure (Structural)    @DalService(operations = {…})
                            ↓ Return 404/405, no OpenAPI

2. Authentication          Spring Security (outside Telaio)
                           ↓ Principal must be authenticated

3. Authorization (Op-level) @DalSecurity + DalAuthAdapter
                           ↓ Principal must have permission for the operation

4. RBAC (Field-level)      DalRbacAdapter
                           ↓ Request/response filtered by role
```

A request reaches each layer only if the earlier layers pass.

## Layer 1: Exposure (Structural Boundaries)

**Purpose**: Hide operations from the API surface entirely.

**Mechanism**: `@DalService(operations = {…})`

```java
@DalService(name = "articles", operations = {DalOperationType.READ, DalOperationType.READ_ONE})
public class ArticleDalService extends JpaDal<Article, Long> {
}
```

This DAL exposes **only** READ and READ_ONE (list and read-one). CREATE, UPDATE, DELETE are not exposed.

**Behavior**:

- Calling `POST /dal/v1/articles` or `PATCH /dal/v1/articles/1` returns **405 Method Not Allowed** with an
  `Allow: GET` header (empty body) — each URI still exposes a GET sibling (`READ` on the collection,
  `READ_ONE` on the item).
- **404 Not Found** (empty body) is returned when the URI exposes no operation at all (e.g. the item URI of an
  append-only DAL declared with `operations = {CREATE, READ}`). An `internal = true` DAL also answers 404, but via a
  different path: it is never registered on the web boundary, so the response is the same "DAL service not found"
  `ProblemDetail` a nonexistent name gets — its existence is not revealed either way.
- The hidden operations are **omitted from OpenAPI documentation**.
- The underlying `Dal` bean retains all methods, so trusted in-process code can still call them.

**When to use**: When the resource is **structurally read-only** for everyone (or for a specific
deployment/environment).

## Layer 2: Authentication

**Purpose**: Verify the principal's identity.

**Responsibility**: Spring Security (not Telaio).

In the showcase, `SecurityConfiguration` sets up HTTP Basic Auth with three test users:

```java
UserDetails developer = User.withUsername("developer")
    .password(encoder.encode("developer"))
    .authorities(UserRole.DEVELOPER)
    .build();

UserDetails admin = User.withUsername("admin")
    .password(encoder.encode("admin"))
    .authorities(UserRole.ADMIN)
    .build();

UserDetails user = User.withUsername("user")
    .password(encoder.encode("user"))
    .authorities(UserRole.USER)
    .build();
```

Send requests with HTTP Basic Auth:

```bash
curl -u developer:developer http://localhost:8080/dal/v1/products
```

If no valid credentials are provided, Spring Security returns **401 Unauthorized** before reaching Telaio.

## Layer 3: Authorization (Operation Level)

**Purpose**: Check if the authenticated principal can perform the requested operation.

**Mechanism**: `@DalSecurity(authAdapterClass = …)` + `DalAuthAdapter<I>`

### Defaults

- **With bare `@DalSecurity`**: Defaults to `DenyAllDalAuthAdapter` — **deny by default** (secure). You must explicitly
  grant access.
- **Without `@DalSecurity` annotation**: Defaults to `PermitAllDalAuthAdapter` — **permit by default** (open). No
  authorization checks.

**This is the critical rule**: The absence of the annotation changes the default from DenyAll to PermitAll.

### DalAuthAdapter Contract

Implement `DalAuthAdapter<I>` to define who can do what:

```java
public interface DalAuthAdapter<I> {
    boolean authorizeCreate(Authentication authentication);

    boolean authorizeRead(Authentication authentication);

    boolean authorizeReadOne(Authentication authentication, I id);

    boolean authorizeUpdate(Authentication authentication, I id);

    boolean authorizeDelete(Authentication authentication, I id);
}
```

Each method returns `true` if the principal is authorized, `false` otherwise.

### Example: Admin-Only Writes

Illustrative — the showcase's actual `ProductAuthAdapter` is more permissive (it also grants
`DEVELOPER` write access via a has-any-role check):

```java
@Component
public class AdminOnlyAuthAdapter implements DalAuthAdapter<Long> {

    @Override
    public boolean authorizeCreate(Authentication auth) {
        return hasRole(auth, UserRole.ADMIN);
    }

    @Override
    public boolean authorizeRead(Authentication auth) {
        return true; // Public read
    }

    @Override
    public boolean authorizeReadOne(Authentication auth, Long id) {
        return true; // Public read-one
    }

    @Override
    public boolean authorizeUpdate(Authentication auth, Long id) {
        return hasRole(auth, UserRole.ADMIN);
    }

    @Override
    public boolean authorizeDelete(Authentication auth, Long id) {
        return hasRole(auth, UserRole.ADMIN);
    }

    private boolean hasRole(Authentication auth, GrantedAuthority role) {
        return auth != null && auth.getAuthorities().contains(role);
    }
}
```

Then declare it on the DAL:

```java
@DalService(name = "products")
@DalSecurity(authAdapterClass = AdminOnlyAuthAdapter.class)
public class ProductDalService extends JpaDal<Product, Long> {
}
```

**Result**: Only ADMINs can create, update, or delete. Everyone else (even unauthenticated) can read.

### Response

If authorization fails, you get **403 Forbidden** with a generic `ProblemDetail` body (no detail is leaked per OWASP):

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "instance": "/dal/v1/products"
}
```

The actual denial reason is **logged to telaio-audit** (DENIED event) and application logs (DEBUG level), never exposed
to the client.

## Layer 4: RBAC (Field Level)

**Purpose**: Filter request and response fields based on role.

**Mechanism**: `DalRbacAdapter<T>`

Even if a principal is authorized to CREATE a PRODUCT, the RBAC adapter might remove sensitive fields like `costPrice`
from the request, or hide them from the response.

### Contract

```java
public interface DalRbacAdapter<T> {
    Map<String, Object> filterInput(DalOperationType operation, Map<String, Object> input, Authentication auth);

    Object filterOutput(DalOperationType operation, T entity, Authentication auth);
}
```

### Strategy 1: Property-Based RBAC

Use `PropertyBasedDalRbacAdapter` for a simple role → field set mapping.

**From the showcase** (`ProductRbacAdapter.java`):

```java
@Component
public class ProductRbacAdapter extends PropertyBasedDalRbacAdapter<Product> {

    @Override
    protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
        return Map.of(
            UserRole.DEVELOPER, Set.of(
                propertyName(Product::getId),
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getCostPrice),
                propertyName(Product::getMarginPercentage),
                propertyName(Product::getProfit),
                propertyName(Product::getSku),
                propertyName(Product::getInternalSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            ),
            UserRole.ADMIN, Set.of(
                propertyName(Product::getId),
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            ),
            UserRole.USER, Set.of(
                propertyName(Product::getId),
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            )
        );
    }

    @Override
    protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
        return Map.of(
            UserRole.DEVELOPER, Set.of(
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getCostPrice),
                propertyName(Product::getSku),
                propertyName(Product::getInternalSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            ),
            UserRole.ADMIN, Set.of(
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            )
        );
    }
}
```

**How it works**:

1. **On input (CREATE/UPDATE)**: The request body is filtered to include only writable fields. Unknown fields are
   dropped.
    - `DEVELOPER` can set `costPrice`; `ADMIN` cannot.
    - `USER` cannot write any field (empty set).

2. **On output (READ/READ_ONE)**: The response entity is filtered to show only readable fields.
    - `DEVELOPER` sees cost data and internal SKU.
    - `ADMIN` sees public and admin-only fields.
    - `USER` sees only public fields (name, price, availability).

**Derived fields**: Fields like `marginPercentage` are computed and only readable (not in the writable map). They appear
in responses but a client cannot set them.

**JSON property names**: The adapter references Java property names (`Product::getCostPrice`), but the framework
translates to JSON names automatically. If your entity has:

```java
@JsonProperty("cost_price")
private Double costPrice;
```

The adapter still uses `propertyName(Product::getCostPrice)`, and the translation to `cost_price` is transparent.

### Strategy 2: JsonView-Based RBAC

Use `JsonViewDalRbacAdapter` to delegate field filtering to Jackson `@JsonView` annotations.

**From the showcase** (`EmployeeRbacAdapter.java`):

```java
@Component
public class EmployeeRbacAdapter extends JsonViewDalRbacAdapter<Employee> {

    @Override
    protected @Nullable Class<?> resolveView(DalOperationType operation, Authentication authentication) {
        if (hasRole(authentication, UserRole.DEVELOPER)) {
            return EmployeeView.Developer.class;
        }
        if (hasRole(authentication, UserRole.ADMIN)) {
            return EmployeeView.Admin.class;
        }
        if (hasRole(authentication, UserRole.USER)) {
            return EmployeeView.User.class;
        }
        return null;  // No recognized role: deny all fields
    }

    private boolean hasRole(@Nullable Authentication authentication, UserRole role) {
        return authentication != null && authentication.getAuthorities().contains(role);
    }
}
```

The `EmployeeView` class hierarchy defines which fields are visible in each view:

```java
public interface EmployeeView {
    interface User extends Department.PublicView {
    }

    interface Admin extends User {
    }

    interface Developer extends Admin {
    }
}
```

Then on the entity, each field is annotated:

```java
@Entity
public class Employee {
    @Id
    @GeneratedValue
    @JsonView(EmployeeView.User.class)
    private Long id;

    @JsonView(EmployeeView.User.class)
    private String name;

    @JsonView(EmployeeView.Admin.class)  // Only admin+ can see
    private String email;

    @JsonView(EmployeeView.Developer.class)  // Only developer can see
    private String internalNotes;
}
```

**Result**: The adapter chooses the view based on the principal's role, and Jackson filters the JSON to match.

### Choosing a Strategy

- **PropertyBased**: Simple role → field set mapping. Easy to understand. Good for straightforward RBAC.
- **JsonView**: More expressive via Jackson annotations. Supports inheritance (User ⊂ Admin ⊂ Developer). Good for role
  hierarchies.

## Authorization vs. Exposure

**Important distinction**:

- **Authorization** (`DalAuthAdapter`): Identity-conditional. "Does this principal have permission?" → 403 if denied.
  The operation is still documented in OpenAPI.
- **Exposure** (`@DalService(operations = …)`): Structural. "Is this operation published?" → 404/405 if not. The
  operation is omitted from OpenAPI.

**Use exposure** for:

- Resources that are read-only for everyone (structural constraint).
- Operations you want to hide entirely from the API.
- Deployment-time decisions (some deployments expose fewer operations).

**Use authorization** for:

- Identity-conditional access control.
- The operation exists, but specific principals are denied.
- Fine-grained permission checks.

## Security Configuration in Spring

Telaio integrates with Spring Security but doesn't replace it. You must still:

1. **Configure Spring Security** (HTTP Basic, OAuth2, LDAP, etc.).
2. **Enable authentication** (the showcase uses HTTP Basic + in-memory users).
3. **Define roles/authorities** (the showcase uses `UserRole.DEVELOPER`, `ADMIN`, `USER`).

Example from the showcase's `SecurityConfiguration`:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(Customizer.withDefaults())  // HTTP Basic Auth
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                auth.anyRequest().authenticated();  // All others require auth
            });
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        // Define test users and their authorities
        return new InMemoryUserDetailsManager(developer, admin, user);
    }
}
```

Your application can use any Spring Security authentication mechanism; Telaio just receives the `Authentication` object
and passes it to your adapters.

## Best Practices

1. **Start with exposure**: If a resource is structurally read-only, use `@DalService(operations = {READ, READ_ONE})`
   rather than denial in an adapter.

2. **Deny by default**: Use bare `@DalSecurity` (which defaults to `DenyAll`) and explicitly grant access in
   `DalAuthAdapter`. This is more secure than `PermitAll`.

3. **Combine layers**: Use exposure for structural boundaries, authorization for operation checks, and RBAC for field
   filtering.

4. **Test your adapters**: Write unit tests for your `DalAuthAdapter` and `DalRbacAdapter` to ensure the filtering is
   correct.

5. **Log authorization denials**: Telaio automatically logs them to `telaio-audit`, so configure your log aggregator to
   flag denials.

6. **Document security in OpenAPI**: If using `telaio-openapi`, the generated spec will include a note that
   authorization is required if `@DalSecurity` is present.

## Example: Full Security Setup

**Entity**:

```java
@Entity
public class Product {
    @Id
    @GeneratedValue
    private Long id;

    @NotBlank
    private String name;

    @NotNull
    private Double price;

    @JsonProperty("cost_price")
    private Double costPrice;

    private String category;
}
```

**Authorization adapter** (the showcase's actual `ProductAuthAdapter`: admins and developers can write;
everyone can read):

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

**RBAC adapter** (developers see cost; admins and users don't; users can't write):

```java
@Component
public class ProductRbacAdapter extends PropertyBasedDalRbacAdapter<Product> {
    @Override
    protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
        return Map.of(
            UserRole.DEVELOPER, Set.of("id", "name", "price", "costPrice", "category"),
            UserRole.ADMIN, Set.of("id", "name", "price", "category"),
            UserRole.USER, Set.of("id", "name", "price", "category")
        );
    }

    @Override
    protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
        return Map.of(
            UserRole.DEVELOPER, Set.of("name", "price", "costPrice", "category"),
            UserRole.ADMIN, Set.of("name", "price", "category"),
            UserRole.USER, Set.of()  // Can't write
        );
    }
}
```

**DAL service**:

```java
@DalService(name = "products")
@DalSecurity(
    authAdapterClass = ProductAuthAdapter.class,
    rbacAdapterClass = ProductRbacAdapter.class
)
public class ProductDalService extends JpaDal<Product, Long> {
}
```

**Result**:

- **DEVELOPER**: Full access (all fields, can create/update/delete).
- **ADMIN**: Can read and write public fields, can't see cost.
- **USER**: Can only read public fields, can't create/update.

All denials are logged to telaio-audit for compliance.
