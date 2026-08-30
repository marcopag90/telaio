package com.paganbit.telaio.security.interceptor;

import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.paganbit.telaio.security.adapter.DalAuthAdapter;
import com.paganbit.telaio.security.adapter.DalRbacAdapter;
import com.paganbit.telaio.security.exception.DalAccessDeniedException;
import com.paganbit.telaio.security.exception.DefaultDalAccessDeniedMessageResolver;
import com.turkraft.springfilter.definition.FilterInfixOperator;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import com.turkraft.springfilter.parser.node.InfixOperationNode;
import com.turkraft.springfilter.parser.node.InputNode;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    @Mock
    private FilterInfixOperator infix;

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
    void read_withoutFilter_shouldNotConsultTheAdapterForFilterFields() throws Throwable {
        when(invocation.getMethod()).thenReturn(readMethod());
        when(invocation.getArguments()).thenReturn(new Object[]{null, null});
        when(authAdapter.authorizeRead(any())).thenReturn(true);
        when(invocation.proceed()).thenReturn(new PageImpl<>(List.of()));

        interceptor.invoke(invocation);

        verify(rbacAdapter, never()).canFilterOn(any(), any());
    }

    @Test
    void read_whenFilterReferencesHiddenField_shouldRejectBeforeProceeding() throws Throwable {
        // The security property: the adapter denies the field, so the read never runs — otherwise the
        // narrowed page would leak the hidden value (bisection through `cost_price > 100`).
        FilterNode filter = comparison("cost_price");
        when(invocation.getMethod()).thenReturn(readMethod());
        when(invocation.getArguments()).thenReturn(new Object[]{filter, null});
        when(authAdapter.authorizeRead(any())).thenReturn(true);
        when(rbacAdapter.canFilterOn(eq("cost_price"), any())).thenReturn(false);

        assertThrows(DalInvalidFilterException.class, () -> interceptor.invoke(invocation));

        verify(invocation, never()).proceed();
        verify(rbacAdapter, never()).filterOutput(any(), any(), any());
        // Operation-level authorization comes first, so a denied read stays a 403, never a 400.
        InOrder inOrder = inOrder(authAdapter, rbacAdapter);
        inOrder.verify(authAdapter).authorizeRead(any());
        inOrder.verify(rbacAdapter).canFilterOn(eq("cost_price"), any());
    }

    @Test
    void read_whenEveryFilterFieldIsReadable_shouldCheckEachFieldAndProceed() throws Throwable {
        FilterNode filter = new InfixOperationNode(comparison("name"), infix, comparison("price"));
        Page<Object> page = new PageImpl<>(List.of("a"));
        when(invocation.getMethod()).thenReturn(readMethod());
        when(invocation.getArguments()).thenReturn(new Object[]{filter, null});
        when(authAdapter.authorizeRead(any())).thenReturn(true);
        when(rbacAdapter.canFilterOn(any(), any())).thenReturn(true);
        when(invocation.proceed()).thenReturn(page);
        when(rbacAdapter.filterOutput(eq(DalOperationType.READ), any(), any()))
            .thenAnswer(inv -> inv.getArgument(1));

        @SuppressWarnings("unchecked")
        Page<Object> result = (Page<Object>) interceptor.invoke(invocation);

        assertNotNull(result);
        assertEquals(List.of("a"), result.getContent());
        verify(rbacAdapter).canFilterOn(eq("name"), any());
        verify(rbacAdapter).canFilterOn(eq("price"), any());
        verify(invocation).proceed();
    }

    @Test
    void read_whenDenied_shouldNotInspectTheFilter() throws Throwable {
        FilterNode filter = comparison("cost_price");
        when(invocation.getMethod()).thenReturn(readMethod());
        when(invocation.getArguments()).thenReturn(new Object[]{filter, null});
        when(authAdapter.authorizeRead(any())).thenReturn(false);

        assertThrows(DalAccessDeniedException.class, () -> interceptor.invoke(invocation));

        verify(invocation, never()).proceed();
        verifyNoInteractions(rbacAdapter);
    }

    @Test
    void readOne_whenDenied_shouldThrowAccessDenied() throws Throwable {
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("readOne", Object.class));
        when(invocation.getArguments()).thenReturn(new Object[]{1L});
        when(authAdapter.authorizeReadOne(any(), eq(1L))).thenReturn(false);

        assertThrows(DalAccessDeniedException.class, () -> interceptor.invoke(invocation));
        verify(invocation, never()).proceed();
        verifyNoInteractions(rbacAdapter);
    }

    @Test
    void readOne_whenAllowed_shouldFilterOutput() throws Throwable {
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("readOne", Object.class));
        when(invocation.getArguments()).thenReturn(new Object[]{1L});
        when(authAdapter.authorizeReadOne(any(), eq(1L))).thenReturn(true);
        when(invocation.proceed()).thenReturn("dto");
        when(rbacAdapter.filterOutput(eq(DalOperationType.READ_ONE), eq("dto"), any())).thenReturn("filteredDto");

        Object result = interceptor.invoke(invocation);

        assertEquals("filteredDto", result);
    }

    @Test
    void update_whenDenied_shouldThrowAccessDenied() throws Throwable {
        when(invocation.getMethod()).thenReturn(
            DalOperationAdapter.class.getMethod("update", Object.class, Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{1L, Map.of("a", 1)});
        when(authAdapter.authorizeUpdate(any(), eq(1L))).thenReturn(false);

        assertThrows(DalAccessDeniedException.class, () -> interceptor.invoke(invocation));
        verify(invocation, never()).proceed();
        verify(rbacAdapter, never()).filterInput(any(), any(), any());
    }

    @Test
    void update_whenAllowed_shouldFilterInputAndOutput() throws Throwable {
        Map<String, Object> patch = Map.of("a", 1);
        Map<String, Object> filteredPatch = Map.of("a", 1, "filtered", true);
        Object[] arguments = {1L, patch};
        when(invocation.getMethod()).thenReturn(
            DalOperationAdapter.class.getMethod("update", Object.class, Map.class));
        when(invocation.getArguments()).thenReturn(arguments);
        when(authAdapter.authorizeUpdate(any(), eq(1L))).thenReturn(true);
        when(rbacAdapter.filterInput(eq(DalOperationType.UPDATE), eq(patch), any())).thenReturn(filteredPatch);
        when(invocation.proceed()).thenReturn(Optional.of("dto"));
        when(rbacAdapter.filterOutput(eq(DalOperationType.UPDATE), eq("dto"), any())).thenReturn("filteredDto");

        Object result = interceptor.invoke(invocation);

        assertEquals(Optional.of("filteredDto"), result);
        assertSame(filteredPatch, arguments[1]);
        // The ordering is the security property: the patch must be filtered BEFORE the operation runs.
        InOrder inOrder = inOrder(rbacAdapter, invocation);
        inOrder.verify(rbacAdapter).filterInput(eq(DalOperationType.UPDATE), eq(patch), any());
        inOrder.verify(invocation).proceed();
        inOrder.verify(rbacAdapter).filterOutput(eq(DalOperationType.UPDATE), eq("dto"), any());
    }

    @Test
    void update_whenEntityMissing_shouldReturnEmptyOptional() throws Throwable {
        Map<String, Object> patch = Map.of("a", 1);
        when(invocation.getMethod()).thenReturn(
            DalOperationAdapter.class.getMethod("update", Object.class, Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{1L, patch});
        when(authAdapter.authorizeUpdate(any(), eq(1L))).thenReturn(true);
        when(rbacAdapter.filterInput(eq(DalOperationType.UPDATE), eq(patch), any())).thenReturn(patch);
        when(invocation.proceed()).thenReturn(Optional.empty());

        Object result = interceptor.invoke(invocation);

        assertEquals(Optional.empty(), result);
        verify(rbacAdapter, never()).filterOutput(any(), any(), any());
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

    private static Method readMethod() throws NoSuchMethodException {
        return DalOperationAdapter.class.getMethod("read", FilterNode.class, Pageable.class);
    }

    /**
     * A {@code field <op> 1} comparison; the operator is a mock — only the tree shape matters here.
     */
    private FilterNode comparison(String field) {
        return new InfixOperationNode(new FieldNode(field), infix, new InputNode(1));
    }

    /**
     * Sub-interface of DalOperationAdapter with an intentionally unannotated method for testing.
     */
    interface UnannotatedAdapter extends DalOperationAdapter<Object, Object> {
        void unannotated();
    }
}
