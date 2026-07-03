package io.paganbit.telaio.security.adapter;

import com.fasterxml.jackson.annotation.JsonView;
import io.paganbit.telaio.core.adapter.DalOperationType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.security.core.Authentication;
import org.springframework.util.CollectionUtils;
import tools.jackson.databind.*;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.ClassIntrospector;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RBAC adapter that delegates field-level filtering to Jackson {@link JsonView}.
 *
 * <p>An alternative to {@link PropertyBasedDalRbacAdapter} for applications whose roles form a
 * <strong>hierarchy</strong> (totally ordered via view-class inheritance, e.g.
 * {@code AdminView extends UserView}). The developer annotates the exposed entity's properties with
 * {@code @JsonView(SomeView.class)} (on the field, or per accessor to differentiate read/write) and
 * implements {@link #resolveView(DalOperationType, Authentication)} to pick the view for the current
 * principal and operation. Output is filtered by serializing through the view; input is filtered by
 * dropping payload keys whose property is not visible in the view, preserving the sparseness required
 * by partial (PATCH) updates.</p>
 *
 * <p><strong>Secure by default:</strong> the adapter disables {@link MapperFeature#DEFAULT_VIEW_INCLUSION}
 * on its own mapper, so a property with <em>no</em> {@code @JsonView} is excluded from every view (it
 * must be explicitly annotated to be exposed/writable). {@link #resolveView} may return {@code null} to
 * deny all fields.</p>
 *
 * <p><strong>Limitations</strong> (by design — choose {@link PropertyBasedDalRbacAdapter} otherwise):
 * a single view is applied per request, so non-hierarchical authority unions cannot be expressed;
 * create-versus-update write differences require returning different views per operation from
 * {@link #resolveView}; the authorization policy lives on the entity (the view annotations).</p>
 *
 * @param <T> the exposed entity type
 * @author Marco Pagan
 * @since 1.0.0
 */
public abstract class JsonViewDalRbacAdapter<T> implements DalRbacAdapter<T> {

    /**
     * Sentinel view that no property is ever tagged with — yields an empty result (deny all).
     */
    private interface NoAccessView {
    }

    /**
     * Shared empty result for properties carrying no {@code @JsonView} (visible in no view).
     */
    private static final Class<?>[] NO_VIEWS = new Class<?>[0];

    private final Class<T> exposedType = resolveExposedType();

    private final Map<Class<?>, Map<String, PropertyInfo>> deserializationProperties = new ConcurrentHashMap<>();

    private ObjectMapper viewMapper = secureViewMapper(JsonMapper.builder().build());

    @Autowired(required = false)
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.viewMapper = secureViewMapper(objectMapper);
    }

    private static ObjectMapper secureViewMapper(ObjectMapper base) {
        return base.rebuild()
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Class<T> resolveExposedType() {
        Class<?>[] args = GenericTypeResolver.resolveTypeArguments(getClass(), JsonViewDalRbacAdapter.class);
        if (args == null || args.length < 1 || args[0] == null) {
            throw new IllegalStateException(
                "Unable to resolve the exposed entity type for " + getClass().getName());
        }
        return (Class<T>) args[0];
    }

    /**
     * Resolves the {@link JsonView} class to apply for the given operation and principal.
     *
     * <p>With a hierarchical view model, return the principal's most-privileged view (its inheritance
     * chain includes the less-privileged views). Return {@code null} to deny all fields.</p>
     *
     * @param operation      the operation being performed
     * @param authentication the current authentication context
     * @return the view class, or {@code null} to expose/accept no field
     */
    protected abstract @Nullable Class<?> resolveView(DalOperationType operation, Authentication authentication);

    // ------------------------------------------------------------------------
    // Input filtering (writes)
    // ------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> filterInput(DalOperationType op, Map<String, Object> input, Authentication auth) {
        if (op != DalOperationType.CREATE && op != DalOperationType.UPDATE) {
            return input; // input filtering applies to writes only
        }
        if (CollectionUtils.isEmpty(input)) {
            return input;
        }
        Class<?> view = resolveView(op, auth);
        JsonNode tree = viewMapper.valueToTree(input);
        if (!(tree instanceof ObjectNode root)) {
            return input;
        }
        pruneInput(root, exposedType, view);
        return viewMapper.convertValue(root, Map.class);
    }

    private void pruneInput(ObjectNode node, Class<?> beanClass, Class<?> view) {
        Map<String, PropertyInfo> properties = deserializationProperties(beanClass);
        for (String field : new ArrayList<>(node.propertyNames())) {
            PropertyInfo info = properties.get(field);
            if (info == null || !isInView(info.views(), view)) {
                node.remove(field);
                continue;
            }
            JsonNode child = node.get(field);
            if (child instanceof ObjectNode objectChild) {
                pruneInput(objectChild, rawContentClass(info.type()), view);
            } else if (child instanceof ArrayNode arrayChild) {
                for (JsonNode element : arrayChild) {
                    if (element instanceof ObjectNode objectElement) {
                        pruneInput(objectElement, rawContentClass(info.type()), view);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Output filtering (responses)
    // ------------------------------------------------------------------------

    @Override
    public Object filterOutput(DalOperationType op, T entity, Authentication auth) {
        if (entity == null) {
            return null;
        }
        Class<?> view = resolveView(op, auth);
        Class<?> effectiveView = view != null ? view : NoAccessView.class;
        Class<?> type = entity.getClass();
        try {
            // Serialize through the view to drop out-of-view fields, then parse the result back into a
            // tree. Returning the tree (rather than re-binding it to an entity) preserves read-only
            // properties — e.g., @JsonProperty(access = READ_ONLY) — that Jackson would otherwise ignore
            // on deserialization, even when they are visible in the active view.
            String json = viewMapper.writerWithView(effectiveView).writeValueAsString(entity);
            return viewMapper.readTree(json);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to filter output for type " + type.getName(), e);
        }
    }

    // ------------------------------------------------------------------------
    // View introspection
    // ------------------------------------------------------------------------

    private boolean isInView(Class<?>[] propertyViews, Class<?> activeView) {
        if (activeView == null) {
            return false;
        }
        for (Class<?> propertyView : propertyViews) {
            if (propertyView.isAssignableFrom(activeView)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, PropertyInfo> deserializationProperties(Class<?> beanClass) {
        return deserializationProperties.computeIfAbsent(beanClass, this::introspectDeserialization);
    }

    private Map<String, PropertyInfo> introspectDeserialization(Class<?> beanClass) {
        DeserializationConfig config = viewMapper.deserializationConfig();
        ClassIntrospector introspector = config.classIntrospectorInstance().forOperation(config);
        JavaType type = viewMapper.constructType(beanClass);
        AnnotatedClass annotatedClass = introspector.introspectClassAnnotations(type);
        BeanDescription description = introspector.introspectForDeserialization(type, annotatedClass);
        Map<String, PropertyInfo> properties = new HashMap<>();
        for (BeanPropertyDefinition property : description.findProperties()) {
            properties.put(property.getName(), new PropertyInfo(property.getPrimaryType(), viewsOf(property)));
        }
        return properties;
    }

    private Class<?>[] viewsOf(BeanPropertyDefinition property) {
        AnnotatedMember[] members = {
            property.getMutator(), property.getField(), property.getPrimaryMember(), property.getGetter()
        };
        for (AnnotatedMember member : members) {
            if (member == null) {
                continue;
            }
            JsonView jsonView = member.getAnnotation(JsonView.class);
            if (jsonView != null) {
                return jsonView.value();
            }
        }
        return NO_VIEWS;
    }

    private Class<?> rawContentClass(JavaType type) {
        JavaType current = type;
        while (current.isContainerType()) {
            current = current.getContentType();
        }
        return current.getRawClass();
    }

    private record PropertyInfo(JavaType type, Class<?>[] views) {

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof PropertyInfo(JavaType otherType, Class<?>[] otherViews)
                && Objects.equals(type, otherType)
                && Arrays.equals(views, otherViews);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(type) + Arrays.hashCode(views);
        }

        @Override
        public @NonNull String toString() {
            return "PropertyInfo[type=" + type + ", views=" + Arrays.toString(views) + "]";
        }
    }
}
