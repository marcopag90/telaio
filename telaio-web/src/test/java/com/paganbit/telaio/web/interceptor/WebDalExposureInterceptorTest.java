package com.paganbit.telaio.web.interceptor;

import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.web.exception.DalOperationNotExposedException;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebDalExposureInterceptorTest {

    private static final Object SENTINEL = new Object();

    @Test
    void shouldProceedForExposedOperation() throws Throwable {
        WebDalExposureInterceptor interceptor = interceptor(DalOperationType.CREATE, DalOperationType.READ);
        MethodInvocation invocation = invocationOf("create", Map.class);
        when(invocation.proceed()).thenReturn(SENTINEL);

        assertThat(interceptor.invoke(invocation)).isSameAs(SENTINEL);
    }

    @Test
    void shouldRejectDisabledOperationWith405WhenUriHasSiblings() {
        // DELETE disabled, but READ_ONE (GET on the same item URI) is exposed.
        WebDalExposureInterceptor interceptor =
            interceptor(DalOperationType.READ_ONE, DalOperationType.UPDATE);
        MethodInvocation invocation = invocationOf("delete", Object.class);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOfSatisfying(DalOperationNotExposedException.class, ex -> {
                assertThat(ex.uriHasNoExposedOperations()).isFalse();
                assertThat(ex.allowedMethods()).contains(HttpMethod.GET, HttpMethod.PATCH);
                assertThat(ex.allowedMethods()).doesNotContain(HttpMethod.DELETE);
            });
    }

    @Test
    void shouldRejectDisabledOperationWith404WhenUriHasNoExposedOperations() {
        // Only collection operations exposed → the item URI exposes nothing.
        WebDalExposureInterceptor interceptor =
            interceptor(DalOperationType.CREATE, DalOperationType.READ);
        MethodInvocation invocation = invocationOf("delete", Object.class);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOfSatisfying(DalOperationNotExposedException.class, ex -> {
                assertThat(ex.uriHasNoExposedOperations()).isTrue();
                assertThat(ex.allowedMethods()).isEmpty();
            });
    }

    @Test
    void shouldRejectDisabledCreateWith405WhenReadExposed() {
        WebDalExposureInterceptor interceptor = interceptor(DalOperationType.READ);
        MethodInvocation invocation = invocationOf("create", Map.class);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOfSatisfying(DalOperationNotExposedException.class, ex -> {
                assertThat(ex.uriHasNoExposedOperations()).isFalse();
                assertThat(ex.allowedMethods()).containsExactly(HttpMethod.GET);
            });
    }

    private static WebDalExposureInterceptor interceptor(DalOperationType... exposed) {
        Set<DalOperationType> set = EnumSet.copyOf(Arrays.asList(exposed));
        return new WebDalExposureInterceptor("feed", set);
    }

    private static MethodInvocation invocationOf(String method, Class<?>... paramTypes) {
        try {
            Method target = DalOperationAdapter.class.getMethod(method, paramTypes);
            MethodInvocation invocation = mock(MethodInvocation.class);
            when(invocation.getMethod()).thenReturn(target);
            return invocation;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
