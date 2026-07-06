package com.paganbit.telaio.web.annotation;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.web.DalRestApiV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalIdArgumentResolverTest {

    @Mock
    private DalManager dalManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NativeWebRequest webRequest;

    @Mock
    private WebDataBinderFactory binderFactory;

    @Mock
    private Dal<Object, Object> dalService;

    @Mock
    private JavaType javaType;

    private DalIdArgumentResolver resolver;
    private Map<String, String> pathVariables;

    @BeforeEach
    void setUp() {
        resolver = new DalIdArgumentResolver(dalManager, objectMapper);
        pathVariables = new HashMap<>();
        pathVariables.put(DalRestApiV1.PATH_VARIABLE_DAL_NAME, "testDal");
        pathVariables.put(DalRestApiV1.PATH_VARIABLE_ID, "123");
    }

    @Test
    void supportsParameter_shouldReturnTrue_whenParameterHasDalIdAnnotation() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("methodWithDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        assertTrue(resolver.supportsParameter(parameter));
    }

    @Test
    void supportsParameter_shouldReturnFalse_whenParameterDoesNotHaveDalIdAnnotation() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("methodWithoutDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        assertFalse(resolver.supportsParameter(parameter));
    }

    @Test
    void resolveArgument_shouldConvertSimpleIdType() throws Exception {
        Method method = TestController.class.getMethod("methodWithDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        )).thenReturn(pathVariables);
        doReturn(dalService).when(dalManager).getServiceByName(anyString());
        doReturn(Long.class).when(dalService).getIdClass();
        doReturn(javaType).when(objectMapper).constructType(any(Class.class));
        doReturn(123L).when(objectMapper).convertValue(anyString(), any(JavaType.class));

        Object result = resolver.resolveArgument(parameter, null, webRequest, binderFactory);

        assertEquals(123L, result);
        verify(objectMapper).convertValue(eq("123"), any(JavaType.class));
    }

    @Test
    void resolveArgument_shouldDecodeAndDeserializeComplexIdType() throws Exception {
        Method method = TestController.class.getMethod("methodWithDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        ComplexId complexId = new ComplexId("test", 123);
        String json = "{\"name\":\"test\",\"value\":123}";
        String encodedId = Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        pathVariables.put(DalRestApiV1.PATH_VARIABLE_ID, encodedId);

        when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        )).thenReturn(pathVariables);
        doReturn(dalService).when(dalManager).getServiceByName(anyString());
        doReturn(ComplexId.class).when(dalService).getIdClass();
        doReturn(javaType).when(objectMapper).constructType(any(Class.class));
        doReturn(complexId).when(objectMapper).readValue(anyString(), any(JavaType.class));

        Object result = resolver.resolveArgument(parameter, null, webRequest, binderFactory);

        assertEquals(complexId, result);
        verify(objectMapper).readValue(eq(json), any(JavaType.class));
    }

    @Test
    void resolveArgument_shouldThrowException_whenPathVariablesAreMissing() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("methodWithDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        )).thenReturn(null);

        assertThrows(IllegalStateException.class, () ->
            resolver.resolveArgument(parameter, null, webRequest, binderFactory));
    }

    @Test
    void resolveArgument_shouldThrowException_whenIdOrDalNameIsMissing() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("methodWithDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        pathVariables.remove(DalRestApiV1.PATH_VARIABLE_ID);
        when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        )).thenReturn(pathVariables);

        assertThrows(IllegalStateException.class, () ->
            resolver.resolveArgument(parameter, null, webRequest, binderFactory));

        pathVariables.put(DalRestApiV1.PATH_VARIABLE_ID, "123");
        pathVariables.remove(DalRestApiV1.PATH_VARIABLE_DAL_NAME);
        when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        )).thenReturn(pathVariables);

        assertThrows(IllegalStateException.class, () ->
            resolver.resolveArgument(parameter, null, webRequest, binderFactory));
    }

    @Test
    void resolveArgument_shouldThrowException_whenBase64IsInvalid() throws NoSuchMethodException {
        Method method = TestController.class.getMethod("methodWithDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        pathVariables.put(DalRestApiV1.PATH_VARIABLE_ID, "!@#$%^&*()");
        when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        )).thenReturn(pathVariables);
        doReturn(dalService).when(dalManager).getServiceByName(anyString());
        doReturn(ComplexId.class).when(dalService).getIdClass();

        assertThrows(IllegalStateException.class, () ->
            resolver.resolveArgument(parameter, null, webRequest, binderFactory));
    }

    @Test
    void resolveArgument_shouldThrowException_whenJsonProcessingFails() throws Exception {
        Method method = TestController.class.getMethod("methodWithDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        String json = "{\"name\":\"test\",\"value\":123}";
        String encodedId = Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        pathVariables.put(DalRestApiV1.PATH_VARIABLE_ID, encodedId);

        when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        )).thenReturn(pathVariables);
        doReturn(dalService).when(dalManager).getServiceByName(anyString());
        doReturn(ComplexId.class).when(dalService).getIdClass();
        doReturn(javaType).when(objectMapper).constructType(any(Class.class));
        doThrow(new JacksonException("Test error") {
        }).when(objectMapper).readValue(anyString(), any(JavaType.class));

        assertThrows(JacksonException.class, () ->
            resolver.resolveArgument(parameter, null, webRequest, binderFactory));
    }

    @Test
    void resolveArgument_shouldUseCustomPathVariableName() throws Exception {
        Method method = TestController.class.getMethod("methodWithCustomDalId", Object.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        pathVariables.put("customId", "456");
        when(webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        )).thenReturn(pathVariables);
        doReturn(dalService).when(dalManager).getServiceByName(anyString());
        doReturn(Long.class).when(dalService).getIdClass();
        doReturn(javaType).when(objectMapper).constructType(any(Class.class));
        doReturn(456L).when(objectMapper).convertValue(eq("456"), any(JavaType.class));

        Object result = resolver.resolveArgument(parameter, null, webRequest, binderFactory);

        assertEquals(456L, result);
        verify(objectMapper).convertValue(eq("456"), any(JavaType.class));
    }

    @SuppressWarnings("unused")
    static class TestController {
        public void methodWithDalId(@DalId Object id) {
            //noop
        }

        public void methodWithCustomDalId(@DalId("customId") Object id) {
            //noop
        }

        public void methodWithoutDalId(Object id) {
            //noop
        }
    }

    record ComplexId(String name, int value) {
    }
}
