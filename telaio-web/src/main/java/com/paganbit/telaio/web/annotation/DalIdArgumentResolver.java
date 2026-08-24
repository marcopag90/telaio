package com.paganbit.telaio.web.annotation;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.rest.contract.DalIdCodec;
import com.paganbit.telaio.web.DalRestApiV1;
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
 * <p>The conversion itself is delegated to the {@link DalIdCodec}.</p>
 *
 * @author Marco Pagan
 * @see DalId
 * @see com.paganbit.telaio.core.Dal#getIdClass()
 * @since 1.0.0
 */
public class DalIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final DalManager dalManager;
    private final DalIdCodec dalIdCodec;

    /**
     * Creates a resolver whose codec uses the given simple-type classification — typically the
     * aggregated predicate carrying backend-contributed types.
     *
     * @param dalManager          the registry resolving the target DAL by name
     * @param objectMapper        the mapper backing the id codec
     * @param simpleTypePredicate the classification deciding raw-vs-Base64 id travel
     */
    public DalIdArgumentResolver(
        DalManager dalManager,
        ObjectMapper objectMapper,
        Predicate<Class<?>> simpleTypePredicate
    ) {
        this.dalManager = dalManager;
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

        Dal<?, ?> dal = dalManager.getServiceByName(dalName);
        return dalIdCodec.decode(rawId, dal.getIdClass());
    }
}
