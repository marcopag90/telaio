package io.paganbit.telaio.openapi.introspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.paganbit.telaio.core.json.JsonPropertyPathResolver;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves the OpenAPI {@link Schema} for an entity (or any supporting) type and registers it — together
 * with every nested schema it depends on — into an {@link OpenAPI} document's {@code components/schemas}.
 *
 * <p>Resolution delegates to swagger-core's {@link ModelConverters}, so Jackson annotations
 * ({@code @JsonProperty}, naming strategy, {@code @JsonIgnore}, {@code @Schema}) and Jakarta Bean
 * Validation constraints on the entity are honored automatically. The returned schema is a {@code $ref}
 * pointing at the registered component, ready to be referenced from request bodies and responses.</p>
 *
 * <p>It additionally re-applies each property's {@link JsonProperty.Access} as OpenAPI
 * {@code readOnly}/{@code writeOnly} on the entity schema. Swagger-core honors {@code @JsonProperty(access)}
 * for scalar properties but <em>drops it for {@code $ref} (relation/object) properties</em>.
 * </p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalEntitySchemaResolver {

    private static final String REF_PREFIX = "#/components/schemas/";

    private final @Nullable JsonPropertyPathResolver jsonPathResolver;

    /**
     * Creates a resolver that does not post-process property access (used where Jackson-access marking is
     * not required, e.g., unit tests).
     */
    public DalEntitySchemaResolver() {
        this(null);
    }

    /**
     * Creates a resolver that re-applies {@code @JsonProperty(access)} to resolved entity schemas.
     *
     * @param jsonPathResolver resolves Java property names to their JSON wire names; when {@code null},
     *                         access post-processing is skipped
     */
    public DalEntitySchemaResolver(@Nullable JsonPropertyPathResolver jsonPathResolver) {
        this.jsonPathResolver = jsonPathResolver;
    }

    /**
     * Resolves the schema for {@code type}, registers it and its nested schemas into {@code openApi}'s
     * components (without overwriting any schema already present), and returns a referencing schema.
     *
     * @param type    the type to resolve (typically a DAL entity, or {@code ProblemDetail})
     * @param openApi the document whose components receive the resolved schemas
     * @return a {@code $ref} schema pointing at the registered component, or an inline object schema
     * when the type cannot be resolved as a named model
     */
    public Schema<Object> resolveAndRegister(Class<?> type, OpenAPI openApi) {
        Components components = ensureComponents(openApi);
        ResolvedSchema resolved = ModelConverters.getInstance()
            .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
        if (resolved == null || resolved.schema == null) {
            return new ObjectSchema();
        }
        if (resolved.referencedSchemas != null) {
            resolved.referencedSchemas.forEach((name, schema) ->
                components.getSchemas().putIfAbsent(name, schema));
        }
        applyJacksonAccess(type, components, resolved.schema.get$ref());
        return asObjectSchema(resolved.schema);
    }

    /**
     * Re-applies {@code @JsonProperty(access)} to the registered entity schema's properties.
     */
    private void applyJacksonAccess(Class<?> type, Components components, @Nullable String entityRef) {
        if (jsonPathResolver == null || entityRef == null || !entityRef.startsWith(REF_PREFIX)) {
            return;
        }
        Schema<?> entitySchema = components.getSchemas().get(entityRef.substring(REF_PREFIX.length()));
        if (entitySchema == null || entitySchema.getProperties() == null) {
            return;
        }
        for (Field field : declaredFields(type)) {
            JsonProperty annotation = field.getAnnotation(JsonProperty.class);
            if (annotation == null) {
                continue;
            }
            if (annotation.access() == JsonProperty.Access.READ_ONLY) {
                markAccess(type, entitySchema, field.getName(), true);
            } else if (annotation.access() == JsonProperty.Access.WRITE_ONLY) {
                markAccess(type, entitySchema, field.getName(), false);
            }
        }
    }

    /**
     * Resolves the field's JSON wire name (serialization view for read-only, deserialization view for
     * write-only, falling back to the other view) and sets {@code readOnly}/{@code writeOnly} on the
     * matching schema property.
     */
    private void markAccess(Class<?> type, Schema<?> entitySchema, String fieldName, boolean readOnly) {
        String jsonName = Objects.requireNonNull(jsonPathResolver).toJsonPath(type, fieldName, readOnly);
        if (jsonName == null) {
            jsonName = jsonPathResolver.toJsonPath(type, fieldName, !readOnly);
        }
        if (jsonName == null) {
            return;
        }
        Schema<?> property = entitySchema.getProperties().get(jsonName);
        if (property == null) {
            return;
        }
        if (readOnly) {
            property.setReadOnly(true);
        } else {
            property.setWriteOnly(true);
        }
    }

    private static Set<Field> declaredFields(Class<?> type) {
        Set<Field> fields = new HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /**
     * Narrows swagger-core's raw {@link ResolvedSchema#schema} to {@code Schema<Object>}. The cast is safe:
     * swagger-core models everything as the same erased {@link Schema} type, and the returned schema is
     * only ever read (referenced) downstream, never written through its type parameter.
     */
    @SuppressWarnings("unchecked")
    private static Schema<Object> asObjectSchema(Schema<?> schema) {
        return (Schema<Object>) schema;
    }

    /**
     * Ensures the document has a {@link Components} with a mutable schema map and returns it.
     *
     * @param openApi the document to prepare
     * @return the document's components, guaranteed to hold a non-null schema map
     */
    public Components ensureComponents(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Components components = openApi.getComponents();
        if (components.getSchemas() == null) {
            components.setSchemas(new LinkedHashMap<>());
        }
        return components;
    }
}
