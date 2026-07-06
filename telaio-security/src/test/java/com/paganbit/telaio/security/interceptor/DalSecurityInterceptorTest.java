package com.paganbit.telaio.security.interceptor;

import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.security.adapter.DalAuthAdapter;
import com.paganbit.telaio.security.adapter.DalRbacAdapter;
import com.paganbit.telaio.security.exception.DalAccessDeniedException;
import com.paganbit.telaio.security.exception.DefaultDalAccessDeniedMessageResolver;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalSecurityInterceptorTest {

    @Mock
    private DalAuthAdapter<Object> authAdapter;
    @Mock
    private DalRbacAdapter<Object> rbacAdapter;
    @Mock
    private MethodInvocation invocation;

    private DalSecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DalSecurityInterceptor(
            "testDal", authAdapter, rbacAdapter, new DefaultDalAccessDeniedMessageResolver());
    }

    @Test
    void create_whenDenied_shouldThrowAccessDenied() throws Throwable {
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of("a", 1)});
        when(authAdapter.authorizeCreate(any())).thenReturn(false);

        assertThrows(DalAccessDeniedException.class, () -> interceptor.invoke(invocation));
        verify(invocation, never()).proceed();
    }

    @Test
    void create_whenAllowed_shouldFilterInputAndOutput() throws Throwable {
        Map<String, Object> input = Map.of("a", 1);
        Map<String, Object> filteredInput = Map.of("a", 1, "filtered", true);
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{input});
        when(authAdapter.authorizeCreate(any())).thenReturn(true);
        when(rbacAdapter.filterInput(eq(DalOperationType.CREATE), eq(input), any())).thenReturn(filteredInput);
        when(invocation.proceed()).thenReturn("dto");
        when(rbacAdapter.filterOutput(eq(DalOperationType.CREATE), eq("dto"), any())).thenReturn("filteredDto");

        Object result = interceptor.invoke(invocation);

        assertEquals("filteredDto", result);
        verify(rbacAdapter).filterInput(eq(DalOperationType.CREATE), eq(input), any());
    }

    @Test
    void read_whenAllowed_shouldFilterEachOutput() throws Throwable {
        Page<Object> page = new PageImpl<>(List.of("a", "b"));
        when(invocation.getMethod()).thenReturn(
            DalOperationAdapter.class.getMethod("read",
                com.turkraft.springfilter.parser.node.FilterNode.class,
                org.springframework.data.domain.Pageable.class));
        when(invocation.getArguments()).thenReturn(new Object[]{null, null});
        when(authAdapter.authorizeRead(any())).thenReturn(true);
        when(invocation.proceed()).thenReturn(page);
        when(rbacAdapter.filterOutput(eq(DalOperationType.READ), any(), any()))
            .thenAnswer(inv -> inv.getArgument(1) + "!");

        @SuppressWarnings("unchecked")
        Page<Object> result = (Page<Object>) interceptor.invoke(invocation);

        assertNotNull(result);
        assertEquals(List.of("a!", "b!"), result.getContent());
    }

    @Test
    void delete_whenAllowed_shouldProceed() throws Throwable {
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("delete", Object.class));
        when(invocation.getArguments()).thenReturn(new Object[]{1L});
        when(authAdapter.authorizeDelete(any(), eq(1L))).thenReturn(true);
        when(invocation.proceed()).thenReturn(null);

        interceptor.invoke(invocation);

        verify(invocation).proceed();
    }

    @Test
    void objectMethod_shouldPassThroughWithoutSecurityChecks() throws Throwable {
        // Object.toString() has no @DalOperation — interceptor must delegate immediately
        when(invocation.getMethod()).thenReturn(Object.class.getMethod("toString"));
        when(invocation.proceed()).thenReturn(null);

        Object result = interceptor.invoke(invocation);

        assertNull(result);
        verify(invocation).proceed();
        verifyNoInteractions(authAdapter, rbacAdapter);
    }

    @Test
    void dalAdapterMethodWithoutAnnotation_shouldThrowIllegalStateException() throws Throwable {
        // A method declared on a DalOperationAdapter sub-interface but missing @DalOperation
        // must fail explicitly — not silently bypass security
        when(invocation.getMethod()).thenReturn(UnannotatedAdapter.class.getMethod("unannotated"));

        assertThrows(IllegalStateException.class, () -> interceptor.invoke(invocation));
        verifyNoInteractions(authAdapter, rbacAdapter);
    }

    /**
     * Sub-interface of DalOperationAdapter with an intentionally unannotated method for testing.
     */
    interface UnannotatedAdapter extends DalOperationAdapter<Object, Object> {
        void unannotated();
    }
}
