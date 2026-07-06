package com.paganbit.telaio.security.adapter;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.CollectionUtils;
import org.springframework.util.function.SingletonSupplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * RBAC adapter based on {@link GrantedAuthority} and field-level access maps, applied to the exposed
 * entity.
 *
 * <p>Filters write payloads against role-keyed <em>writable</em> field maps and response entities
 * against role-keyed <em>readable</em> field maps. Field paths use dot notation and are authored with
 * the type-safe {@code PropertyNameResolver} DSL (Java property names); they are translated to the
 * corresponding JSON names via Jackson introspection — using the <em>deserialization</em> view on input
 * and the <em>serialization</em> view on output — so filtering stays faithful to the wire representation
 * under any {@code PropertyNamingStrategy} or {@code @JsonProperty} rename declared on the entity.</p>
 *
 * <p>Subclasses must implement {@link #readableFieldsByRole()} and {@link #writableFieldsByRole()}.
 * The base-writable map applies to both create and update; the base-readable map to all output
 * operations. For finer control, override the per-operation hooks
 * ({@link #createWritableFieldsByRole()}, {@link #updateWritableFieldsByRole()},
 * {@link #createReadableFieldsByRole()}, {@link #readReadableFieldsByRole()},
 * {@link #updateReadableFieldsByRole()}), each defaulting to the corresponding base map.</p>
 *
 * <p>The authorities of the current {@link Authentication} are unioned at request time
 * ({@link #resolveEffectiveFields(Authentication, Map)}), so a principal holding several roles sees the
 * union of their fields. The {@code *FieldsByRole()} maps are treated as constants: each is invoked at
 * most once per adapter instance (lazily) and memoized.</p>
 *
 * <p>Output filtering prunes the entity's Jackson tree ({@code valueToTree} &rarr; prune) and returns
 * that tree as a serialization-ready projection; this supports immutable types and records, inherited
 * fields and nested collections out of the box, and preserves read-only properties (e.g.
 * {@code @JsonProperty(access = READ_ONLY)}) that rebinding the tree to an entity would drop.</p>
 *
 * @param <T> the exposed entity type
 * @author Marco Pagan
 * @since 1.0.0
 */
@SuppressWarnings({"squid:S3776"})
public abstract class PropertyBasedDalRbacAdapter<T> implements DalRbacAdapter<T> {

    /**
     * Readable field map applied to every output operation (create/read/read-one/update responses).
     */
    protected abstract Map<GrantedAuthority, Set<String>> readableFieldsByRole();

    /**
     * Base writable field map applied to both create and update operations.
     */
    protected abstract Map<GrantedAuthority, Set<String>> writableFieldsByRole();

    /**
     * Writable map for create operations. Defaults to {@link #writableFieldsByRole()}.
     */
    protected Map<GrantedAuthority, Set<String>> createWritableFieldsByRole() {
        return writableFields.get();
    }

    /**
     * Writable map for update operations. Defaults to {@link #writableFieldsByRole()}.
     */
    protected Map<GrantedAuthority, Set<String>> updateWritableFieldsByRole() {
        return writableFields.get();
    }

    /**
     * Readable map for the create response. Defaults to {@link #readableFieldsByRole()}.
     */
    protected Map<GrantedAuthority, Set<String>> createReadableFieldsByRole() {
        return readableFields.get();
    }

    /**
     * Readable map for read and read-one responses. Defaults to {@link #readableFieldsByRole()}.
     */
    protected Map<GrantedAuthority, Set<String>> readReadableFieldsByRole() {
        return readableFields.get();
    }

    /**
     * Readable map for the update response. Defaults to {@link #readableFieldsByRole()}.
     */
    protected Map<GrantedAuthority, Set<String>> updateReadableFieldsByRole() {
        return readableFields.get();
    }

    private final Supplier<Map<GrantedAuthority, Set<String>>> readableFields =
        SingletonSupplier.of(this::readableFieldsByRole);
    private final Supplier<Map<GrantedAuthority, Set<String>>> writableFields =
        SingletonSupplier.of(this::writableFieldsByRole);
    private final Supplier<Map<GrantedAuthority, Set<String>>> createWritable =
        SingletonSupplier.of(this::createWritableFieldsByRole);
    private final Supplier<Map<GrantedAuthority, Set<String>>> updateWritable =
        SingletonSupplier.of(this::updateWritableFieldsByRole);
    private final Supplier<Map<GrantedAuthority, Set<String>>> createReadable =
        SingletonSupplier.of(this::createReadableFieldsByRole);
    private final Supplier<Map<GrantedAuthority, Set<String>>> readReadable =
        SingletonSupplier.of(this::readReadableFieldsByRole);
    private final Supplier<Map<GrantedAuthority, Set<String>>> updateReadable =
        SingletonSupplier.of(this::updateReadableFieldsByRole);

    /**
     * The exposed entity type, resolved from the concrete subclass's type argument. Needed to translate
     * input field paths (no entity instance is available on the write path).
     */
    private final Class<T> exposedType = resolveExposedType();

    /**
     * Jackson mapper used for tree pruning. Injected with the application mapper when running in Spring;
     * defaults to a standalone mapper, so the adapter is usable without a container (e.g., tests).
     */
    private ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * Resolves Java-property field paths to their JSON-name equivalents (caches per type/view).
     */
    private JsonPropertyPathResolver pathResolver = new JsonPropertyPathResolver(objectMapper);

    @Autowired(required = false)
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.pathResolver = new JsonPropertyPathResolver(objectMapper);
    }

    @SuppressWarnings("unchecked")
    private Class<T> resolveExposedType() {
        Class<?>[] args = GenericTypeResolver.resolveTypeArguments(getClass(), PropertyBasedDalRbacAdapter.class);
        if (args == null || args.length < 1 || args[0] == null) {
            throw new IllegalStateException(
                "Unable to resolve the exposed entity type for " + getClass().getName());
        }
        return (Class<T>) args[0];
    }

    // ------------------------------------------------------------------------
    // Input filtering (writes)
    // ------------------------------------------------------------------------

    @Override
    public Map<String, Object> filterInput(DalOperationType op, Map<String, Object> input, Authentication auth) {
        Map<GrantedAuthority, Set<String>> map = switch (op) {
            case CREATE -> createWritable.get();
            case UPDATE -> updateWritable.get();
            default -> null; // input filtering applies to writes only
        };
        if (map == null) {
            return input;
        }
        Set<String> allowedJsonPaths = toJsonPaths(exposedType, resolveEffectiveFields(auth, map), false);
        return filterInputMap(input, allowedJsonPaths, "");
    }

    /**
     * Recursively filters a map of input values based on allowed field paths (JSON names), handling
     * nested objects and lists of nested objects using dot notation.
     */
    protected Map<String, Object> filterInputMap(Map<String, Object> input, Set<String> allowedFields, String prefix) {
        if (CollectionUtils.isEmpty(input)) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String fieldPath = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            boolean exact = allowedFields.contains(fieldPath);
            boolean hasDescendant = hasDescendant(allowedFields, fieldPath);
            if (!exact && !hasDescendant) {
                continue;
            }
            Object value = entry.getValue();
            if (exact) {
                result.put(entry.getKey(), value);
            } else if (value instanceof Map<?, ?> nested) {
                Map<String, Object> filtered = filterInputMap(castToStringMap(nested), allowedFields, fieldPath);
                if (!filtered.isEmpty()) {
                    result.put(entry.getKey(), filtered);
                }
            } else if (value instanceof List<?> list) {
                List<Object> filtered = filterInputList(list, allowedFields, fieldPath);
                if (!filtered.isEmpty()) {
                    result.put(entry.getKey(), filtered);
                }
            }
        }
        return result;
    }

    private List<Object> filterInputList(List<?> list, Set<String> allowedFields, String fieldPath) {
        List<Object> result = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Map<?, ?> nested) {
                Map<String, Object> filtered = filterInputMap(castToStringMap(nested), allowedFields, fieldPath);
                if (!filtered.isEmpty()) {
                    result.add(filtered);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToStringMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    // ------------------------------------------------------------------------
    // Output filtering (responses)
    // ------------------------------------------------------------------------

    @Override
    public Object filterOutput(DalOperationType op, T entity, Authentication auth) {
        if (entity == null) {
            return null;
        }
        Map<GrantedAuthority, Set<String>> map = switch (op) {
            case CREATE -> createReadable.get();
            case UPDATE -> updateReadable.get();
            default -> readReadable.get();
        };
        return pruneOutput(entity, resolveEffectiveFields(auth, map));
    }

    /**
     * Prunes an entity to the allowed fields by operating on its Jackson tree, and returns that pruned
     * tree as a serialization-ready projection.
     *
     * <p>The tree is returned as-is (not rebound to a {@code T} instance): rebinding would silently drop
     * properties Jackson does not deserialize back onto a bean — notably
     * {@code @JsonProperty(access = READ_ONLY)} fields such as a generated {@code id} — even when they
     * are visible to the principal. Operating on the tree also supports immutable types, records,
     * inherited fields, and nested collections without reconstruction.</p>
     */
    protected JsonNode pruneOutput(T entity, Set<String> allowedJavaFields) {
        Class<?> type = entity.getClass();
        try {
            JsonNode tree = objectMapper.valueToTree(entity);
            if (!(tree instanceof ObjectNode root)) {
                return tree;
            }
            Set<String> allowedJsonPaths = toJsonPaths(type, allowedJavaFields, true);
            prune(root, allowedJsonPaths, "");
            return root;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to filter output for type " + type.getName(), e);
        }
    }

    private void prune(ObjectNode node, Set<String> allowedPaths, String prefix) {
        for (String field : new ArrayList<>(node.propertyNames())) {
            String path = prefix.isEmpty() ? field : prefix + "." + field;
            boolean exact = allowedPaths.contains(path);
            boolean hasDescendant = hasDescendant(allowedPaths, path);
            JsonNode child = node.get(field);
            if (child instanceof ObjectNode objectChild) {
                if (exact || hasDescendant) {
                    prune(objectChild, allowedPaths, path);
                    if (objectChild.isEmpty()) {
                        node.remove(field);
                    }
                } else {
                    node.remove(field);
                }
            } else if (child instanceof ArrayNode arrayChild) {
                if (exact || hasDescendant) {
                    pruneArray(arrayChild, allowedPaths, path);
                    if (arrayChild.isEmpty()) {
                        node.remove(field);
                    }
                } else {
                    node.remove(field);
                }
            } else if (!exact) {
                node.remove(field);
            }
        }
    }

    private void pruneArray(ArrayNode array, Set<String> allowedPaths, String path) {
        for (JsonNode element : array) {
            if (element instanceof ObjectNode objectElement) {
                prune(objectElement, allowedPaths, path);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Java-property -> JSON name translation
    // ------------------------------------------------------------------------

    /**
     * Translates Java-property-name field paths into their JSON-name equivalents for the given root type,
     * honoring {@code PropertyNamingStrategy} and {@code @JsonProperty}. Paths that cannot be resolved are
     * dropped (deny by default). Delegates to the shared {@link JsonPropertyPathResolver}.
     *
     * @param forSerialization {@code true} to use the serialization view (output), {@code false} for the
     *                         deserialization view (input)
     */
    protected Set<String> toJsonPaths(Class<?> rootType, Set<String> javaPaths, boolean forSerialization) {
        return pathResolver.toJsonPaths(rootType, javaPaths, forSerialization);
    }

    // ------------------------------------------------------------------------
    // Effective-field resolution
    // ------------------------------------------------------------------------

    /**
     * Resolves the readable fields granted to the given authentication for read/read-one responses.
     */
    protected Set<String> getReadableFieldsFor(Authentication auth) {
        return resolveEffectiveFields(auth, readReadable.get());
    }

    /**
     * Flattens the field sets of every authority the authentication holds (union).
     */
    protected Set<String> resolveEffectiveFields(Authentication auth, Map<GrantedAuthority, Set<String>> fieldMap) {
        if (auth == null || fieldMap == null) {
            return Collections.emptySet();
        }
        return auth.getAuthorities().stream()
            .map(fieldMap::get)
            .filter(Objects::nonNull)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    }

    private static boolean hasDescendant(Set<String> paths, String path) {
        String prefix = path + ".";
        for (String candidate : paths) {
            if (candidate.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
