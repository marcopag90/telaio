package com.paganbit.telaio.audit.interceptor;

import com.paganbit.telaio.audit.event.DalAuditEvent;
import com.paganbit.telaio.audit.event.DalAuditEventStore;
import com.paganbit.telaio.audit.event.DalAuditOutcome;
import com.paganbit.telaio.audit.event.DalAuditOutcomeClassifier;
import com.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalAuditDeniedInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-06-12T10:00:00Z");

    @Mock
    private DalAuditEventStore store;
    @Mock
    private DalAuditPrincipalResolver principalResolver;
    @Mock
    private DalAuditOutcomeClassifier outcomeClassifier;
    @Mock
    private MethodInvocation invocation;

    private DalAuditDeniedInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DalAuditDeniedInterceptor(
            "testDal", EnumSet.allOf(DalOperationType.class), store, principalResolver, outcomeClassifier,
            null, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void deniedAttempt_shouldBeStoredAndRethrown() throws Throwable {
        AccessDeniedException failure = new AccessDeniedException("forbidden");
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of("name", "Widget")});
        when(invocation.proceed()).thenThrow(failure);
        when(outcomeClassifier.classify(failure)).thenReturn(DalAuditOutcome.DENIED);
        when(principalResolver.resolvePrincipal()).thenReturn("user");

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(failure);

        ArgumentCaptor<DalAuditEvent> captor = ArgumentCaptor.forClass(DalAuditEvent.class);
        verify(store).store(captor.capture());
        DalAuditEvent event = captor.getValue();
        assertThat(event.timestamp()).isEqualTo(NOW);
        assertThat(event.dalName()).isEqualTo("testDal");
        assertThat(event.operation()).isEqualTo(DalOperationType.CREATE);
        assertThat(event.principal()).isEqualTo("user");
        assertThat(event.arguments()).isEqualTo(Map.of("input", Map.of("name", "Widget")));
        assertThat(event.outcome()).isEqualTo(DalAuditOutcome.DENIED);
        assertThat(event.errorType()).isEqualTo(AccessDeniedException.class.getName());
        assertThat(event.errorMessage()).isEqualTo("forbidden");
    }

    @Test
    void nonDeniedFailure_shouldBeRethrownWithoutStoring() throws Throwable {
        // The DAL-level interceptor already records errors that reach the DAL
        RuntimeException failure = new RuntimeException("boom");
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of()});
        when(invocation.proceed()).thenThrow(failure);
        when(outcomeClassifier.classify(failure)).thenReturn(DalAuditOutcome.ERROR);

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(failure);

        verifyNoInteractions(store);
    }

    @Test
    void successfulOperation_shouldNotBeStored() throws Throwable {
        // The DAL-level interceptor already records successful operations
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of()});
        when(invocation.proceed()).thenReturn("dto");

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("dto");
        verifyNoInteractions(store, outcomeClassifier);
    }

    @Test
    void operationOutsideAuditedSet_shouldPassThrough() throws Throwable {
        interceptor = new DalAuditDeniedInterceptor(
            "testDal", EnumSet.of(DalOperationType.CREATE), store, principalResolver, outcomeClassifier,
            null, Clock.fixed(NOW, ZoneOffset.UTC));
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("delete", Object.class));
        when(invocation.proceed()).thenReturn(null);

        interceptor.invoke(invocation);

        verify(invocation).proceed();
        verifyNoInteractions(store, principalResolver, outcomeClassifier);
    }

    @Test
    void storeFailure_shouldNotMaskTheDeniedException() throws Throwable {
        AccessDeniedException failure = new AccessDeniedException("forbidden");
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of()});
        when(invocation.proceed()).thenThrow(failure);
        when(outcomeClassifier.classify(failure)).thenReturn(DalAuditOutcome.DENIED);
        doThrow(new IllegalStateException("store down")).when(store).store(any());

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(failure);
    }
}
