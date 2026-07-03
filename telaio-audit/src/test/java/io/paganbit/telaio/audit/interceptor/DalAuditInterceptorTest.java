package io.paganbit.telaio.audit.interceptor;

import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.audit.event.DalAuditEvent;
import io.paganbit.telaio.audit.event.DalAuditEventStore;
import io.paganbit.telaio.audit.event.DalAuditOutcome;
import io.paganbit.telaio.audit.event.DalAuditOutcomeClassifier;
import io.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.adapter.DalOperationType;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalAuditInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-06-12T10:00:00Z");

    @Mock
    private DalAuditEventStore store;
    @Mock
    private DalAuditPrincipalResolver principalResolver;
    @Mock
    private DalAuditOutcomeClassifier outcomeClassifier;
    @Mock
    private MethodInvocation invocation;

    private DalAuditInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DalAuditInterceptor(
            "testDal", EnumSet.allOf(DalOperationType.class), store, principalResolver, outcomeClassifier,
            null, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void create_whenSuccessful_shouldStoreSuccessEvent() throws Throwable {
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of("name", "Widget")});
        when(invocation.proceed()).thenReturn("entity");
        when(principalResolver.resolvePrincipal()).thenReturn("admin");

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("entity");
        DalAuditEvent event = storedEvent();
        assertThat(event.timestamp()).isEqualTo(NOW);
        assertThat(event.dalName()).isEqualTo("testDal");
        assertThat(event.operation()).isEqualTo(DalOperationType.CREATE);
        assertThat(event.principal()).isEqualTo("admin");
        assertThat(event.arguments()).isEqualTo(Map.of("input", Map.of("name", "Widget")));
        assertThat(event.outcome()).isEqualTo(DalAuditOutcome.SUCCESS);
        assertThat(event.errorType()).isNull();
        assertThat(event.errorMessage()).isNull();
        assertThat(event.duration()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
        verifyNoInteractions(outcomeClassifier);
    }

    @Test
    void create_whenFailing_shouldStoreClassifiedEventAndRethrow() throws Throwable {
        RuntimeException failure = new RuntimeException("boom");
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of("name", "Widget")});
        when(invocation.proceed()).thenThrow(failure);
        when(outcomeClassifier.classify(failure)).thenReturn(DalAuditOutcome.ERROR);

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(failure);

        DalAuditEvent event = storedEvent();
        assertThat(event.outcome()).isEqualTo(DalAuditOutcome.ERROR);
        assertThat(event.errorType()).isEqualTo(RuntimeException.class.getName());
        assertThat(event.errorMessage()).isEqualTo("boom");
    }

    @Test
    void create_whenClassifiedAsDenied_shouldStoreDeniedEvent() throws Throwable {
        RuntimeException failure = new RuntimeException("denied");
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of()});
        when(invocation.proceed()).thenThrow(failure);
        when(outcomeClassifier.classify(failure)).thenReturn(DalAuditOutcome.DENIED);

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(failure);

        assertThat(storedEvent().outcome()).isEqualTo(DalAuditOutcome.DENIED);
    }

    @Test
    void operationOutsideAuditedSet_shouldPassThroughWithoutStoring() throws Throwable {
        interceptor = new DalAuditInterceptor(
            "testDal", EnumSet.of(DalOperationType.CREATE), store, principalResolver, outcomeClassifier,
            null, Clock.fixed(NOW, ZoneOffset.UTC));
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("delete", Object.class));
        when(invocation.proceed()).thenReturn(null);

        interceptor.invoke(invocation);

        verify(invocation).proceed();
        verifyNoInteractions(store, principalResolver, outcomeClassifier);
    }

    @Test
    void methodWithoutDalOperation_shouldPassThroughWithoutStoring() throws Throwable {
        when(invocation.getMethod()).thenReturn(Object.class.getMethod("toString"));
        when(invocation.proceed()).thenReturn("stub");

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("stub");
        verifyNoInteractions(store, principalResolver, outcomeClassifier);
    }

    @Test
    void annotationDeclaredOnInterface_shouldBeFoundThroughConcreteMethod() throws Throwable {
        // CGLIB proxies dispatch on the concrete class method, which does not itself carry
        // @DalOperation — the interceptor must resolve it from the Dal interface
        when(invocation.getMethod()).thenReturn(StubDal.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of("a", 1)});
        when(invocation.proceed()).thenReturn("entity");

        interceptor.invoke(invocation);

        assertThat(storedEvent().operation()).isEqualTo(DalOperationType.CREATE);
    }

    @Test
    void storeFailure_shouldNeverBreakTheBusinessResult() throws Throwable {
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of()});
        when(invocation.proceed()).thenReturn("entity");
        doThrow(new IllegalStateException("store down")).when(store).store(any());

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("entity");
    }

    @Test
    void storeFailure_shouldNotMaskTheBusinessException() throws Throwable {
        RuntimeException failure = new RuntimeException("boom");
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of()});
        when(invocation.proceed()).thenThrow(failure);
        when(outcomeClassifier.classify(failure)).thenReturn(DalAuditOutcome.ERROR);
        doThrow(new IllegalStateException("store down")).when(store).store(any());

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(failure);
    }

    @Test
    void argumentSnapshot_shouldNotReflectLaterMutations() throws Throwable {
        Map<String, Object> input = new HashMap<>(Map.of("name", "Widget"));
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{input});
        when(invocation.proceed()).thenAnswer(inv -> {
            // Simulates an inner concern mutating the shared argument map (e.g. RBAC filtering)
            input.put("injected", true);
            return "entity";
        });

        interceptor.invoke(invocation);

        assertThat(storedEvent().arguments()).isEqualTo(Map.of("input", Map.of("name", "Widget")));
    }

    @Test
    void readArguments_shouldBeRecordedAsStringifiedFilterAndPageable() throws Throwable {
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("read", FilterNode.class, Pageable.class));
        when(invocation.getArguments()).thenReturn(new Object[]{null, PageRequest.of(0, 10)});
        when(invocation.proceed()).thenReturn(new PageImpl<>(List.of()));

        interceptor.invoke(invocation);

        DalAuditEvent event = storedEvent();
        assertThat(event.operation()).isEqualTo(DalOperationType.READ);
        assertThat(event.arguments())
            .containsEntry("filter", null)
            .containsEntry("pageable", PageRequest.of(0, 10).toString());
    }

    @Test
    void readFilter_shouldBeRenderedAsQueryString() throws Throwable {
        FilterStringConverter converter = mock(FilterStringConverter.class);
        FilterNode filter = mock(FilterNode.class);
        when(converter.convert(filter)).thenReturn("name:'John'");
        interceptor = new DalAuditInterceptor(
            "testDal", EnumSet.allOf(DalOperationType.class), store, principalResolver, outcomeClassifier,
            converter, Clock.fixed(NOW, ZoneOffset.UTC));
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("read", FilterNode.class, Pageable.class));
        when(invocation.getArguments()).thenReturn(new Object[]{filter, PageRequest.of(0, 10)});
        when(invocation.proceed()).thenReturn(new PageImpl<>(List.of()));

        interceptor.invoke(invocation);

        assertThat(storedEvent().arguments()).containsEntry("filter", "name:'John'");
    }

    @Test
    void readFilter_withoutConverter_shouldFallBackToToString() throws Throwable {
        FilterNode filter = mock(FilterNode.class);
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("read", FilterNode.class, Pageable.class));
        when(invocation.getArguments()).thenReturn(new Object[]{filter, PageRequest.of(0, 10)});
        when(invocation.proceed()).thenReturn(new PageImpl<>(List.of()));

        interceptor.invoke(invocation);

        assertThat(storedEvent().arguments()).containsEntry("filter", String.valueOf(filter));
    }

    @Test
    void readFilter_whenRenderingFails_shouldFallBackWithoutBreakingTheOperation() throws Throwable {
        FilterStringConverter converter = mock(FilterStringConverter.class);
        FilterNode filter = mock(FilterNode.class);
        when(converter.convert(filter)).thenThrow(new IllegalStateException("render failure"));
        interceptor = new DalAuditInterceptor(
            "testDal", EnumSet.allOf(DalOperationType.class), store, principalResolver, outcomeClassifier,
            converter, Clock.fixed(NOW, ZoneOffset.UTC));
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("read", FilterNode.class, Pageable.class));
        when(invocation.getArguments()).thenReturn(new Object[]{filter, PageRequest.of(0, 10)});
        when(invocation.proceed()).thenReturn(new PageImpl<>(List.of()));

        interceptor.invoke(invocation);

        DalAuditEvent event = storedEvent();
        assertThat(event.outcome()).isEqualTo(DalAuditOutcome.SUCCESS);
        assertThat(event.arguments()).containsEntry("filter", String.valueOf(filter));
    }

    @Test
    void readOneArguments_shouldBeRecordedAsId() throws Throwable {
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("readOne", Object.class));
        when(invocation.getArguments()).thenReturn(new Object[]{42L});
        when(invocation.proceed()).thenReturn(Optional.empty());

        interceptor.invoke(invocation);

        DalAuditEvent event = storedEvent();
        assertThat(event.operation()).isEqualTo(DalOperationType.READ_ONE);
        assertThat(event.arguments()).isEqualTo(Map.of("id", 42L));
    }

    @Test
    void updateArguments_shouldBeRecordedAsIdAndPatch() throws Throwable {
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("update", Object.class, Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{42L, Map.of("name", "Updated")});
        when(invocation.proceed()).thenReturn(Optional.of("entity"));

        interceptor.invoke(invocation);

        DalAuditEvent event = storedEvent();
        assertThat(event.operation()).isEqualTo(DalOperationType.UPDATE);
        assertThat(event.arguments()).isEqualTo(Map.of("id", 42L, "patch", Map.of("name", "Updated")));
    }

    @Test
    void deleteArguments_shouldBeRecordedAsId() throws Throwable {
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("delete", Object.class));
        when(invocation.getArguments()).thenReturn(new Object[]{42L});
        when(invocation.proceed()).thenReturn(null);

        interceptor.invoke(invocation);

        DalAuditEvent event = storedEvent();
        assertThat(event.operation()).isEqualTo(DalOperationType.DELETE);
        assertThat(event.arguments()).isEqualTo(Map.of("id", 42L));
    }

    private DalAuditEvent storedEvent() {
        ArgumentCaptor<DalAuditEvent> captor = ArgumentCaptor.forClass(DalAuditEvent.class);
        verify(store).store(captor.capture());
        return captor.getValue();
    }

    abstract static class StubDal implements Dal<Object, Long> {

        @Override
        public Object create(Map<String, Object> properties) {
            return properties;
        }
    }
}
