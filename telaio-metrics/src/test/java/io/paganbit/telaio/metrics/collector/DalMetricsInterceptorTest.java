package io.paganbit.telaio.metrics.collector;

import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.adapter.DalOperationType;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalMetricsInterceptorTest {

    @Mock
    private DalMetricsRecorder aggregator;
    @Mock
    private MethodInvocation invocation;

    private DalMetricsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DalMetricsInterceptor(
            "testDal", EnumSet.allOf(DalOperationType.class), List.of(aggregator));
    }

    @Test
    void successfulOperation_shouldRecordSuccess() throws Throwable {
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.proceed()).thenReturn("entity");

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("entity");
        verify(aggregator).doRecord(eq("testDal"), eq(DalOperationType.CREATE), anyLong(), eq(false));
    }

    @Test
    void failingOperation_shouldRecordErrorAndRethrow() throws Throwable {
        RuntimeException failure = new RuntimeException("boom");
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("delete", Object.class));
        when(invocation.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(failure);

        verify(aggregator).doRecord(eq("testDal"), eq(DalOperationType.DELETE), anyLong(), eq(true));
    }

    @Test
    void operationOutsideMeasuredSet_shouldPassThroughWithoutRecording() throws Throwable {
        interceptor = new DalMetricsInterceptor("testDal", EnumSet.of(DalOperationType.CREATE), List.of(aggregator));
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("delete", Object.class));
        when(invocation.proceed()).thenReturn(null);

        interceptor.invoke(invocation);

        verify(invocation).proceed();
        verifyNoInteractions(aggregator);
    }

    @Test
    void methodWithoutDalOperation_shouldPassThroughWithoutRecording() throws Throwable {
        when(invocation.getMethod()).thenReturn(Object.class.getMethod("toString"));
        when(invocation.proceed()).thenReturn("stub");

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("stub");
        verifyNoInteractions(aggregator);
    }

    @Test
    void aggregatorFailure_shouldNotBreakTheBusinessResult() throws Throwable {
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.proceed()).thenReturn("entity");
        doThrow(new IllegalStateException("aggregator down"))
            .when(aggregator).doRecord(eq("testDal"), eq(DalOperationType.CREATE), anyLong(), anyBoolean());

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("entity");
    }

    @Test
    void aggregatorFailure_shouldNotMaskTheBusinessException() throws Throwable {
        RuntimeException failure = new RuntimeException("boom");
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.proceed()).thenThrow(failure);
        doThrow(new IllegalStateException("aggregator down"))
            .when(aggregator).doRecord(eq("testDal"), eq(DalOperationType.CREATE), anyLong(), anyBoolean());

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(failure);
    }

    @Test
    void multipleRecorders_shouldAllReceiveTheMeasurement_andOneFailureNotStopTheOthers() throws Throwable {
        DalMetricsRecorder first = mock(DalMetricsRecorder.class);
        DalMetricsRecorder second = mock(DalMetricsRecorder.class);
        doThrow(new IllegalStateException("first down"))
            .when(first).doRecord(eq("testDal"), eq(DalOperationType.CREATE), anyLong(), anyBoolean());
        interceptor = new DalMetricsInterceptor(
            "testDal", EnumSet.allOf(DalOperationType.class), List.of(first, second));
        when(invocation.getMethod()).thenReturn(Dal.class.getMethod("create", Map.class));
        when(invocation.proceed()).thenReturn("entity");

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("entity");
        verify(first).doRecord(eq("testDal"), eq(DalOperationType.CREATE), anyLong(), eq(false));
        verify(second).doRecord(eq("testDal"), eq(DalOperationType.CREATE), anyLong(), eq(false));
    }

    @Test
    void readOperation_shouldBeMeasured() throws Throwable {
        when(invocation.getMethod()).thenReturn(
            Dal.class.getMethod("read", com.turkraft.springfilter.parser.node.FilterNode.class,
                org.springframework.data.domain.Pageable.class));
        when(invocation.proceed()).thenReturn(null);

        interceptor.invoke(invocation);

        verify(aggregator).doRecord(eq("testDal"), eq(DalOperationType.READ), anyLong(), eq(false));
        verify(aggregator, never()).doRecord(eq("testDal"), eq(DalOperationType.CREATE), anyLong(), anyBoolean());
    }
}
