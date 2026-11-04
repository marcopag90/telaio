package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.exception.DalOperationNotExposedException;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DalExposureInterceptorTest {

    private static final DalChannel CHANNEL = DalChannel.of("test");
    private static final Object SENTINEL = new Object();

    @Test
    void shouldProceedForExposedOperation() throws Throwable {
        DalExposureInterceptor interceptor = interceptor(EnumSet.of(DalOperationType.CREATE, DalOperationType.READ));
        MethodInvocation invocation = invocationOf(DalOperationAdapter.class, "create", Map.class);
        when(invocation.proceed()).thenReturn(SENTINEL);

        assertThat(interceptor.invoke(invocation)).isSameAs(SENTINEL);
    }

    @Test
    void shouldRejectNonExposedOperationWithDalOperationAndChannel() throws Throwable {
        DalExposureInterceptor interceptor = interceptor(EnumSet.of(DalOperationType.READ));
        MethodInvocation invocation = invocationOf(DalOperationAdapter.class, "delete", Object.class);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOfSatisfying(DalOperationNotExposedException.class, ex -> {
                assertThat(ex.dalName()).isEqualTo("feed");
                assertThat(ex.operation()).isEqualTo(DalOperationType.DELETE);
                assertThat(ex.channel()).isEqualTo(CHANNEL);
            });
        verify(invocation, never()).proceed();
    }

    @Test
    void emptyExposedSet_shouldRejectEveryOperation() throws Throwable {
        DalExposureInterceptor interceptor = interceptor(EnumSet.noneOf(DalOperationType.class));
        MethodInvocation invocation = invocationOf(DalOperationAdapter.class, "create", Map.class);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOf(DalOperationNotExposedException.class);
        verify(invocation, never()).proceed();
    }

    @Test
    void shouldNotShareTheCallerSet() throws Throwable {
        Set<DalOperationType> exposed = EnumSet.of(DalOperationType.CREATE);
        DalExposureInterceptor interceptor = interceptor(exposed);
        exposed.clear();
        MethodInvocation invocation = invocationOf(DalOperationAdapter.class, "create", Map.class);
        when(invocation.proceed()).thenReturn(SENTINEL);

        assertThat(interceptor.invoke(invocation)).isSameAs(SENTINEL);
    }

    @Test
    void methodWithoutDalOperation_outsideTheAdapter_shouldPassThrough() throws Throwable {
        DalExposureInterceptor interceptor = interceptor(EnumSet.noneOf(DalOperationType.class));
        MethodInvocation invocation = invocationOf(Object.class, "toString");
        when(invocation.proceed()).thenReturn(SENTINEL);

        assertThat(interceptor.invoke(invocation)).isSameAs(SENTINEL);
    }

    @Test
    void adapterMethodWithoutDalOperation_shouldFailFast() {
        DalExposureInterceptor interceptor = interceptor(EnumSet.allOf(DalOperationType.class));
        MethodInvocation invocation = invocationOf(UnannotatedAdapter.class, "extra");

        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("@DalOperation");
    }

    private static DalExposureInterceptor interceptor(Set<DalOperationType> exposed) {
        return new DalExposureInterceptor("feed", CHANNEL, exposed);
    }

    private static MethodInvocation invocationOf(Class<?> type, String method, Class<?>... paramTypes) {
        try {
            Method target = type.getMethod(method, paramTypes);
            MethodInvocation invocation = mock(MethodInvocation.class);
            when(invocation.getMethod()).thenReturn(target);
            return invocation;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * An adapter sub-interface declaring an operation without {@link DalOperation}: a programming error
     * the interceptor must surface instead of silently letting the call through.
     */
    interface UnannotatedAdapter extends DalOperationAdapter<Object, Object> {

        void extra();
    }
}
