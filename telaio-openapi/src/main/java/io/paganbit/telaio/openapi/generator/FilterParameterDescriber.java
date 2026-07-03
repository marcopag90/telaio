package io.paganbit.telaio.openapi.generator;

import io.paganbit.telaio.core.json.JsonPropertyPathResolver;
import io.paganbit.telaio.introspection.TypeUtil;
import io.paganbit.telaio.web.DalRestApiV1;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.utils.Constants;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the OpenAPI {@code q} query {@link Parameter} for a DAL entity.
 *
 * <p>The description is intentionally terse — a one-line pointer to the Turkraft Spring Filter language.
 * The operator set is Spring Filter's standard (no value in repeating it on every endpoint) and the
 * filterable fields are already visible in the entity schema, so neither is duplicated here. When examples
 * are enabled, a single ready-to-paste example filter, derived from the entity's own fields (using the
 * <em>JSON</em> wire names via {@link JsonPropertyPathResolver}), is attached.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class FilterParameterDescriber {

    private static final String FILTER_DESCRIPTION =
        "Optional filter expression in the "
            + "[Turkraft Spring Filter](https://github.com/turkraft/springfilter) query language.";

    private final JsonPropertyPathResolver jsonPathResolver;
    private final boolean includeExamples;

    public FilterParameterDescriber(JsonPropertyPathResolver jsonPathResolver, boolean includeExamples) {
        this.jsonPathResolver = jsonPathResolver;
        this.includeExamples = includeExamples;
    }

    /**
     * Builds the {@code q} filter parameter for the given entity type.
     *
     * @param entityClass the DAL entity whose fields are filterable
     * @return the populated {@code q} query parameter
     */
    public Parameter describe(Class<?> entityClass) {
        Parameter parameter = new Parameter()
            .name(DalRestApiV1.REQUEST_PARAM_FILTER)
            .in(Constants.QUERY_PARAM)
            .required(false)
            .description(FILTER_DESCRIPTION)
            .schema(new StringSchema());

        if (includeExamples) {
            String example = exampleFilter(filterableFields(entityClass));
            if (example != null) {
                parameter.example(example);
            }
        }
        return parameter;
    }

    /**
     * Reflects over the entity's instance fields (including inherited ones) and keeps those that are
     * serialized (resolvable to a JSON name) and of a simple, filterable type.
     */
    private List<FieldInfo> filterableFields(Class<?> entityClass) {
        List<FieldInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> current = entityClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                FieldInfo info = toFilterableField(entityClass, field, seen);
                if (info != null) {
                    result.add(info);
                }
            }
        }
        return result;
    }

    /**
     * Maps a declared field to a {@link FieldInfo} when it is a distinct, serialized, simple-typed field,
     * returning {@code null} otherwise. {@code seen} tracks field names already encountered across the
     * class hierarchy so a subclass field shadows a superclass field of the same name.
     */
    private @Nullable FieldInfo toFilterableField(Class<?> entityClass, Field field, Set<String> seen) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            return null;
        }
        if (!seen.add(field.getName())) {
            return null;
        }
        if (TypeUtil.isComplexType(field.getType())) {
            return null;
        }
        String jsonName = jsonPathResolver.toJsonPath(entityClass, field.getName(), true);
        return jsonName == null ? null : new FieldInfo(jsonName, field.getType());
    }

    /**
     * Derives a single, conservative example filter: prefer a {@code String} field (like match), then a
     * numeric field (greater-than), then any field (equality).
     */
    private @Nullable String exampleFilter(List<FieldInfo> fields) {
        for (FieldInfo field : fields) {
            if (field.type() == String.class) {
                return field.jsonName() + " ~ '*text*'";
            }
        }
        for (FieldInfo field : fields) {
            if (Number.class.isAssignableFrom(boxed(field.type()))) {
                return field.jsonName() + " > 0";
            }
        }
        return fields.isEmpty() ? null : fields.getFirst().jsonName() + " : 'value'";
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        return type;
    }

    /**
     * A filterable field's JSON wire name and its (boxed-or-raw) declared type.
     */
    private record FieldInfo(String jsonName, Class<?> type) {
    }
}
