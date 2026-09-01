package com.paganbit.telaio.core.json;

import com.paganbit.telaio.introspection.DefaultSimpleTypePredicate;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.ClassIntrospector;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates dot-notation property paths between <em>Java</em> names and <em>JSON</em> wire names
 * for a given root type. {@code @JsonProperty} and {@code PropertyNamingStrategy} renames are
 * honored, across nested objects and collections.
 *
 * <p>The forward direction ({@link #toJsonPath}/{@link #toJsonPaths}) maps Java names to the names
 * clients see on the wire; an unresolvable segment yields {@code null}. The reverse direction maps
 * wire names back to Java names: {@link #resolveJavaPath} reports the first segment that does not
 * resolve, {@link #toJavaPath} is its lenient form. Resolution uses Jackson introspection; per-class
 * name maps are cached.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class JsonPropertyPathResolver {

    /**
     * Reference accessor segments a backend may append to a property path (e.g. the keys of a Mongo
     * {@code DBRef}); they are never checked against the bean.
     */
    private static final Set<String> REFERENCE_KEY_SEGMENTS = Set.of("$id", "$ref", "$db");

    private static final DefaultSimpleTypePredicate SIMPLE_TYPES = new DefaultSimpleTypePredicate();

    private final ObjectMapper objectMapper;

    private final Map<Class<?>, Map<String, PropertyMeta>> serializationNames = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, PropertyMeta>> deserializationNames = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, PropertyMeta>> javaNames = new ConcurrentHashMap<>();

    public JsonPropertyPathResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * The outcome of a reverse ({@code JSON → Java}) path resolution.
     *
     * @param javaPath          the path translated to Java property names; segments that could not be
     *                          resolved are kept verbatim
     * @param unresolvedSegment the first segment that failed to resolve on a bean type, or {@code null}
     *                          when every checked segment resolved
     * @since 1.2.0
     */
    public record JavaPathResolution(String javaPath, @Nullable String unresolvedSegment) {

        /**
         * Whether the whole path resolved (no segment failed on a bean type).
         *
         * @return {@code true} when {@link #unresolvedSegment()} is {@code null}
         */
        public boolean resolved() {
            return unresolvedSegment == null;
        }
    }

    /**
     * Translates a single dot-notation Java-property path into its JSON-name equivalent.
     *
     * @param rootType         the type the path is rooted at
     * @param javaPath         the dot-notation path of Java property names (e.g. {@code address.zipCode})
     * @param forSerialization {@code true} to use the serialization view (output) — a property Jackson never
     *                         writes (e.g. {@code @JsonProperty(access = WRITE_ONLY)}) does not resolve in
     *                         it; {@code false} for the deserialization view (input)
     * @return the translated JSON-name path, or {@code null} if any segment cannot be resolved
     */
    public @Nullable String toJsonPath(Class<?> rootType, String javaPath, boolean forSerialization) {
        JavaType currentType = objectMapper.constructType(rootType);
        StringBuilder jsonPath = new StringBuilder();
        for (String part : javaPath.split("\\.")) {
            Class<?> beanClass = rawContentClass(currentType);
            PropertyMeta meta = jsonNames(beanClass, forSerialization).get(part);
            if (meta == null) {
                return null;
            }
            appendSegment(jsonPath, meta.name());
            currentType = meta.type();
        }
        return jsonPath.toString();
    }

    /**
     * Translates a single dot-notation JSON-name path into its underlying Java-property path — the
     * reverse of {@link #toJsonPath}. Resolution honors {@code PropertyNamingStrategy} and
     * {@code @JsonProperty} renames in either Jackson view (serialization and deserialization names are
     * both accepted), and also accepts the Java names of the properties and declared fields.
     *
     * <p>This translation is <em>lenient</em>: a segment that cannot be matched is appended verbatim
     * (and so are all segments after it), so the caller always receives a usable path. Use
     * {@link #resolveJavaPath} to learn whether a segment failed.</p>
     *
     * @param rootType the type the path is rooted at
     * @param jsonPath the dot-notation path of JSON wire names (e.g. {@code address.zip_code})
     * @return the path translated to Java property names, with unresolvable segments left unchanged
     */
    public String toJavaPath(Class<?> rootType, String jsonPath) {
        return resolveJavaPath(rootType, jsonPath).javaPath();
    }

    /**
     * Resolves a dot-notation JSON-name path against {@code rootType}, translating every segment to its
     * Java property name and reporting the first segment that does not exist on the bean type it is
     * applied to.
     *
     * <p>Segments are checked as long as the current type is a bean (collections and arrays are unwrapped
     * to their element type); a segment applied to a scalar type is reported as unresolved. Checking
     * stops — and the remaining segments pass through verbatim — once the path enters a type whose keys
     * are not introspectable ({@code Map}, {@code Object}, {@code JsonNode}) or reaches a reference
     * accessor segment ({@code $id}, {@code $ref}, {@code $db} — the keys of a stored document
     * reference).</p>
     *
     * @param rootType the type the path is rooted at
     * @param jsonPath the dot-notation path of JSON (or Java) property names
     * @return the resolution outcome, never {@code null}
     * @since 1.2.0
     */
    public JavaPathResolution resolveJavaPath(Class<?> rootType, String jsonPath) {
        JavaType currentType = objectMapper.constructType(rootType);
        StringBuilder javaPath = new StringBuilder();
        String unresolvedSegment = null;
        boolean checking = true;
        for (String part : jsonPath.split("\\.")) {
            if (checking) {
                JavaType element = unwrapElements(currentType);
                if (isReferenceKeySegment(part) || isOpaque(element)) {
                    // Dynamic keys or a reference accessor: this and every remaining segment pass through.
                    checking = false;
                } else if (isLeaf(element)) {
                    // A segment applied to a scalar can never resolve.
                    unresolvedSegment = part;
                    checking = false;
                } else {
                    PropertyMeta meta = javaNames(element.getRawClass()).get(part);
                    if (meta != null) {
                        appendSegment(javaPath, meta.name());
                        currentType = meta.type();
                        continue;
                    }
                    // Unknown on a bean type: report it, and stop tracking the type chain — this and every
                    // remaining segment pass through unchanged.
                    unresolvedSegment = part;
                    checking = false;
                }
            }
            appendSegment(javaPath, part);
        }
        return new JavaPathResolution(javaPath.toString(), unresolvedSegment);
    }

    /**
     * Translates a set of Java-property paths into their JSON-name equivalents, dropping any path that
     * cannot be resolved (deny by default).
     *
     * @param rootType         the type the paths are rooted at
     * @param javaPaths        the dot-notation paths of Java property names
     * @param forSerialization {@code true} to use the serialization view (output), {@code false} for the
     *                         deserialization view (input)
     * @return the resolvable paths translated to JSON names
     */
    public Set<String> toJsonPaths(Class<?> rootType, Set<String> javaPaths, boolean forSerialization) {
        Set<String> result = new HashSet<>();
        for (String javaPath : javaPaths) {
            String jsonPath = toJsonPath(rootType, javaPath, forSerialization);
            if (jsonPath != null) {
                result.add(jsonPath);
            }
        }
        return result;
    }

    private static void appendSegment(StringBuilder path, String segment) {
        if (!path.isEmpty()) {
            path.append('.');
        }
        path.append(segment);
    }

    private Map<String, PropertyMeta> jsonNames(Class<?> beanClass, boolean forSerialization) {
        Map<Class<?>, Map<String, PropertyMeta>> cache = forSerialization ? serializationNames : deserializationNames;
        return cache.computeIfAbsent(beanClass, type -> introspectJsonNames(type, forSerialization));
    }

    private Map<String, PropertyMeta> introspectJsonNames(Class<?> beanClass, boolean forSerialization) {
        // Forward: Java internal name -> JSON name. Only properties Jackson would actually write (getter or
        // field left after access filtering) belong to the serialization view: a WRITE_ONLY property is
        // still listed by the introspector but never appears in the output.
        Map<String, PropertyMeta> names = new HashMap<>();
        for (BeanPropertyDefinition property : introspect(beanClass, forSerialization)) {
            if (forSerialization && !property.couldSerialize()) {
                continue;
            }
            names.put(property.getInternalName(), new PropertyMeta(property.getName(), property.getPrimaryType()));
        }
        return names;
    }

    private Map<String, PropertyMeta> javaNames(Class<?> beanClass) {
        return javaNames.computeIfAbsent(beanClass, this::introspectJavaNames);
    }

    private Map<String, PropertyMeta> introspectJavaNames(Class<?> beanClass) {
        // Reverse: JSON name -> Java internal name, in three precedence tiers so that a wire name always
        // wins over a Java name that happens to spell the same:
        //   1. JSON names of both views (the deserialization view wins on conflict), so renamed fields
        //      resolve regardless of read-only / write-only access;
        //   2. Java internal names of the same properties, so a filter already using them keeps working;
        //   3. names of the declared fields Jackson does not expose (e.g. @JsonIgnore), so server-side
        //      filters and in-process callers addressing them by Java name keep working too.
        List<BeanPropertyDefinition> properties = new ArrayList<>(introspect(beanClass, false));
        properties.addAll(introspect(beanClass, true));
        Map<String, PropertyMeta> names = new HashMap<>();
        for (BeanPropertyDefinition property : properties) {
            names.putIfAbsent(property.getName(), meta(property));
        }
        for (BeanPropertyDefinition property : properties) {
            names.putIfAbsent(property.getInternalName(), meta(property));
        }
        for (Class<?> type = beanClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    names.putIfAbsent(field.getName(),
                        new PropertyMeta(field.getName(), objectMapper.constructType(field.getGenericType())));
                }
            }
        }
        return names;
    }

    private static PropertyMeta meta(BeanPropertyDefinition property) {
        return new PropertyMeta(property.getInternalName(), property.getPrimaryType());
    }

    private List<BeanPropertyDefinition> introspect(Class<?> beanClass, boolean forSerialization) {
        JavaType type = objectMapper.constructType(beanClass);
        BeanDescription description = forSerialization
            ? introspector(objectMapper.serializationConfig()).introspectForSerialization(
            type, classAnnotations(beanClass, true))
            : introspector(objectMapper.deserializationConfig()).introspectForDeserialization(
            type, classAnnotations(beanClass, false));
        return description.findProperties();
    }

    private ClassIntrospector introspector(MapperConfig<?> config) {
        return config.classIntrospectorInstance().forOperation(config);
    }

    private AnnotatedClass classAnnotations(Class<?> beanClass, boolean forSerialization) {
        MapperConfig<?> config = forSerialization
            ? objectMapper.serializationConfig()
            : objectMapper.deserializationConfig();
        return introspector(config).introspectClassAnnotations(objectMapper.constructType(beanClass));
    }

    /**
     * Unwraps container types (collections, arrays, maps) to the raw class of their content.
     */
    private Class<?> rawContentClass(JavaType type) {
        JavaType current = type;
        while (current.isContainerType()) {
            current = current.getContentType();
        }
        return current.getRawClass();
    }

    // ------------------------------------------------------------------------
    // Path-walking rules, shared with every component that walks a field path segment by segment
    // (the strict filter rewrite, the RBAC filter-field checks) so that they cannot drift apart.
    // ------------------------------------------------------------------------

    /**
     * Whether {@code segment} is a reference accessor a backend may append to a property path — the keys
     * of a stored document reference ({@code $id}, {@code $ref}, {@code $db}). Such a segment, and every
     * segment after it, is never checked against the bean.
     *
     * @param segment one dot-separated segment of a field path
     * @return {@code true} for a reference accessor segment
     * @since 1.2.0
     */
    public static boolean isReferenceKeySegment(String segment) {
        return REFERENCE_KEY_SEGMENTS.contains(segment);
    }

    /**
     * Unwraps collections and arrays (not maps) to their element type, so a path through a
     * {@code List<Contact>} resolves against {@code Contact}.
     *
     * @param type the declared type of the current property
     * @return the element type, or {@code type} itself when it is not a collection or array
     * @since 1.2.0
     */
    public static JavaType unwrapElements(JavaType type) {
        JavaType current = type;
        while (current.isCollectionLikeType() || current.isArrayType()) {
            current = current.getContentType();
        }
        return current;
    }

    /**
     * Whether the segments applied to {@code element} cannot be checked against declared properties: maps
     * (dynamic keys), {@code Object} (unknown shape) and raw JSON trees.
     *
     * @param element the (element-unwrapped) type a segment is applied to
     * @return {@code true} when the type has no introspectable properties
     * @since 1.2.0
     */
    public static boolean isOpaque(JavaType element) {
        Class<?> raw = element.getRawClass();
        return element.isMapLikeType() || Object.class.equals(raw) || JsonNode.class.isAssignableFrom(raw);
    }

    /**
     * Whether {@code element} is a scalar (primitive, wrapper, string, number, temporal, enum, ...) that
     * has no addressable sub-properties, even though reflection would expose getters on it.
     *
     * @param element the (element-unwrapped) type a segment is applied to
     * @return {@code true} for a scalar type
     * @since 1.2.0
     */
    public static boolean isLeaf(JavaType element) {
        return SIMPLE_TYPES.test(element.getRawClass());
    }

    /**
     * A resolved property's target name (the JSON name in the forward map, the Java internal name in
     * the reverse map) together with its declared type for walking nested paths.
     */
    private record PropertyMeta(String name, JavaType type) {
    }
}
