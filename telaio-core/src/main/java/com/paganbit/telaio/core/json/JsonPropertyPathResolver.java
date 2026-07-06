package com.paganbit.telaio.core.json;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.ClassIntrospector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates dot-notation field paths between their <em>Java</em> property names and their
 * <em>JSON</em>-name equivalents for a given root type, honoring {@code PropertyNamingStrategy} and
 * {@code @JsonProperty} renames declared on the entity, across nested objects and collections.
 *
 * <p>The forward direction ({@link #toJsonPath}/{@link #toJsonPaths}) maps Java property names to the
 * names clients see on the wire, using the <em>serialization</em> or <em>deserialization</em> view.
 * The reverse direction ({@link #toJavaPath}) maps JSON wire names back to the underlying Java property
 * names so query layers (e.g., filtering) can speak the names clients actually send. Resolution uses
 * Jackson introspection; per-class name maps are cached (keyed by type and direction/view).</p>
 *
 * <p>Forward resolution is strict — an unresolvable segment yields {@code null} (deny by default).
 * Reverse resolution is lenient — an unresolvable segment passes through unchanged, so non-renamed and
 * unknown fields keep working and the downstream layer (not this resolver) decides how to handle them.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class JsonPropertyPathResolver {

    private final ObjectMapper objectMapper;

    private final Map<Class<?>, Map<String, PropertyMeta>> serializationNames = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, PropertyMeta>> deserializationNames = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<String, PropertyMeta>> javaNames = new ConcurrentHashMap<>();

    public JsonPropertyPathResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Translates a single dot-notation Java-property path into its JSON-name equivalent.
     *
     * @param rootType         the type the path is rooted at
     * @param javaPath         the dot-notation path of Java property names (e.g. {@code address.zipCode})
     * @param forSerialization {@code true} to use the serialization view (output), {@code false} for the
     *                         deserialization view (input)
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
     * both accepted).
     *
     * <p>This translation is <em>lenient</em>: any segment that cannot be matched to a known JSON name
     * is appended verbatim (and so are all segments after it), so non-renamed fields, Java names that
     * were never renamed, and genuinely unknown fields all pass through. The caller therefore always
     * receives a usable path, and the downstream layer reports unknown-field errors as it sees fit.</p>
     *
     * @param rootType the type the path is rooted at
     * @param jsonPath the dot-notation path of JSON wire names (e.g. {@code address.zip_code})
     * @return the path translated to Java property names, with unresolvable segments left unchanged
     */
    public String toJavaPath(Class<?> rootType, String jsonPath) {
        JavaType currentType = objectMapper.constructType(rootType);
        StringBuilder javaPath = new StringBuilder();
        boolean resolvable = true;
        for (String part : jsonPath.split("\\.")) {
            if (resolvable) {
                Class<?> beanClass = rawContentClass(currentType);
                PropertyMeta meta = javaNames(beanClass).get(part);
                if (meta != null) {
                    appendSegment(javaPath, meta.name());
                    currentType = meta.type();
                    continue;
                }
                // Once a segment is unknown, we can no longer track the type chain reliably, so this
                // and every remaining segment are passed through unchanged.
                resolvable = false;
            }
            appendSegment(javaPath, part);
        }
        return javaPath.toString();
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
        // Forward: Java internal name -> JSON name.
        Map<String, PropertyMeta> names = new HashMap<>();
        for (BeanPropertyDefinition property : introspect(beanClass, forSerialization)) {
            names.put(property.getInternalName(), new PropertyMeta(property.getName(), property.getPrimaryType()));
        }
        return names;
    }

    private Map<String, PropertyMeta> javaNames(Class<?> beanClass) {
        return javaNames.computeIfAbsent(beanClass, this::introspectJavaNames);
    }

    private Map<String, PropertyMeta> introspectJavaNames(Class<?> beanClass) {
        // Reverse: JSON name -> Java internal name. Union both views so renamed fields resolve
        // regardless of read-only / write-only access; the deserialization view wins on conflict.
        Map<String, PropertyMeta> names = new HashMap<>();
        for (BeanPropertyDefinition property : introspect(beanClass, false)) {
            names.putIfAbsent(
                property.getName(),
                new PropertyMeta(property.getInternalName(), property.getPrimaryType())
            );
        }
        for (BeanPropertyDefinition property : introspect(beanClass, true)) {
            names.putIfAbsent(
                property.getName(),
                new PropertyMeta(property.getInternalName(), property.getPrimaryType())
            );
        }
        return names;
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

    /**
     * A resolved property's target name (the JSON name in the forward map, the Java internal name in
     * the reverse map) together with its declared type for walking nested paths.
     */
    private record PropertyMeta(String name, JavaType type) {
    }
}
