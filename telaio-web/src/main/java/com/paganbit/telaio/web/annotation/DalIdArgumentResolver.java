package com.paganbit.telaio.web.annotation;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import com.paganbit.telaio.core.registry.DalCatalog;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.rest.contract.DalIdCodec;
import com.paganbit.telaio.web.DalRestApiV1;
import com.paganbit.telaio.web.channel.DalRestChannelDefinition;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Argument resolver for the {@link DalId} annotation.
 *
 * <p>Dynamically converts the ID path variable into the appropriate Java type based on the DAL
 * context. It supports both simple IDs (e.g. {@code Long}, {@code String}) and complex IDs
 * (composite keys), which are expected to be passed as Base64-encoded JSON objects.</p>
 *
 * <p>The conversion itself is delegated to the {@link DalIdCodec}. The target DAL is resolved through its
 * registered definition, never by looking a bean up under the client-supplied path segment: an unknown
 * name, an internal DAL and a DAL that exposes nothing on REST all raise {@link DalNotFoundException}, so
 * the item routes answer exactly like the collection route and reveal none of them.</p>
 *
 * @author Marco Pagan
 * @see DalId
 * @see com.paganbit.telaio.core.Dal#getIdClass()
 * @since 1.0.0
 */
public class DalIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final DalCatalog catalog;
    private final DalRestChannelDefinition restChannel;
    private final DalIdCodec dalIdCodec;

    /**
     * Creates a resolver whose codec uses the given simple-type classification — typically the
     * aggregated predicate carrying backend-contributed types.
     *
     * @param catalog             the registry resolving the target DAL and its definition by name
     * @param restChannel         the REST channel definition, telling whether the DAL is on REST at all
     * @param objectMapper        the mapper backing the id codec
     * @param simpleTypePredicate the classification deciding raw-vs-Base64 id travel
     */
    public DalIdArgumentResolver(
        DalCatalog catalog,
        DalRestChannelDefinition restChannel,
        ObjectMapper objectMapper,
        Predicate<Class<?>> simpleTypePredicate
    ) {
        this.catalog = catalog;
        this.restChannel = restChannel;
        this.dalIdCodec = new DalIdCodec(objectMapper, simpleTypePredicate);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(DalId.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object resolveArgument(
        @NonNull MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        final var pathVariables = (Map<String, String>) webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        );
        if (pathVariables == null) {
            throw new IllegalStateException("Missing URI template variables.");
        }

        DalId dalId = parameter.getParameterAnnotation(DalId.class);
        String idVariableName = Objects.requireNonNull(dalId).value();
        String rawId = pathVariables.get(idVariableName);
        String dalName = pathVariables.get(DalRestApiV1.PATH_VARIABLE_DAL_NAME);

        if (StringUtils.isBlank(rawId) || StringUtils.isBlank(dalName)) {
            throw new IllegalStateException("Missing '%s' or '%s' in URI path."
                .formatted(idVariableName, DalRestApiV1.PATH_VARIABLE_DAL_NAME));
        }

        Dal<?, ?> dal = exposedDal(dalName);
        return dalIdCodec.decode(rawId, dal.getIdClass());
    }

    /**
     * Resolves the DAL named by the path segment through its definition. An unknown name is rejected by
     * the registry; an internal DAL, or one that exposes nothing on REST, is rejected here with the same
     * exception, so that neither is distinguishable from a name that does not exist — not even by the
     * shape of its id, which is never decoded for them.
     */
    private Dal<?, ?> exposedDal(String dalName) {
        DalDefinitionEntry definition = catalog.getDefinitionByName(dalName);
        if (definition.internal() || restChannel.exposedOperations(definition.dalClass()).isEmpty()) {
            throw new DalNotFoundException(dalName);
        }
        return catalog.getServiceByName(definition.name());
    }
}
