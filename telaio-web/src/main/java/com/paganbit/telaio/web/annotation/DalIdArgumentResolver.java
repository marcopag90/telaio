package com.paganbit.telaio.web.annotation;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.introspection.TypeUtil;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Argument resolver for the {@link DalId} annotation.
 *
 * <p>Dynamically converts the ID path variable into the appropriate Java type based on the DAL
 * context. It supports both simple IDs (e.g. {@code Long}, {@code String}) and complex IDs
 * (composite keys), which are expected to be passed as Base64-encoded JSON objects.</p>
 *
 * <p>If the ID type is simple, the raw path variable is converted using Jackson's
 * {@code convertValue}. If the ID type is complex (a POJO or record), the path variable must be
 * Base64 URL-safe encoded JSON, which is decoded and deserialized into the proper class.</p>
 *
 * @author Marco Pagan
 * @see DalId
 * @see com.paganbit.telaio.core.Dal#getIdClass()
 * @since 1.0.0
 */
public class DalIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final DalManager dalManager;
    private final ObjectMapper objectMapper;

    public DalIdArgumentResolver(DalManager dalManager, ObjectMapper objectMapper) {
        this.dalManager = dalManager;
        this.objectMapper = objectMapper;
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
    ) throws JacksonException {
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
        Class<?> idType = dal.getIdClass();
        if (TypeUtil.isComplexType(idType)) {
            String decodedJson = decodeRawIdValueFromBase64(rawId);
            return objectMapper.readValue(decodedJson, objectMapper.constructType(idType));
        }
        return objectMapper.convertValue(rawId, objectMapper.constructType(idType));
    }

    private static String decodeRawIdValueFromBase64(String rawId) {
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(rawId);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decode composite ID from Base64: " + rawId, e);
        }
    }
}
