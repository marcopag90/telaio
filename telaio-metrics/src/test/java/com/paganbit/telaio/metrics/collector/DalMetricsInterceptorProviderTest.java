package com.paganbit.telaio.metrics.collector;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.interceptor.DalInterceptionContext;
import com.paganbit.telaio.core.interceptor.DalInterceptorProvider;
import com.paganbit.telaio.metrics.annotation.DalMetrics;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DalMetricsInterceptorProviderTest {

    private final DalMetricsRecorder recorder = mock(DalMetricsRecorder.class);
    private final DalMetricsInterceptorProvider provider =
        new DalMetricsInterceptorProvider(List.of(recorder));

    @Test
    void dalWithoutAnnotation_shouldBeMeasuredByDefault() {
        assertThat(provider.getInterceptor(contextOf(PlainDal.class))).isNotNull();
    }

    @Test
    void noRecorders_shouldContributeNoInterceptor() {
        DalMetricsInterceptorProvider empty = new DalMetricsInterceptorProvider(List.of());
        assertThat(empty.getInterceptor(contextOf(PlainDal.class))).isNull();
    }

    @Test
    void dalWithMetricsDisabled_shouldNotBeMeasured() {
        assertThat(provider.getInterceptor(contextOf(DisabledDal.class))).isNull();
    }

    @Test
    void dalWithMetricsEnabled_shouldBeMeasured() {
        assertThat(provider.getInterceptor(contextOf(EnabledDal.class))).isNotNull();
    }

    @Test
    void noAnnotation_shouldMeasureAllOperations() {
        assertThat(provider.measuredOperations(null))
            .isEqualTo(EnumSet.allOf(DalOperationType.class));
    }

    @Test
    void emptyOperations_shouldMeasureAllOperations() {
        DalMetrics annotation = EnabledDal.class.getAnnotation(DalMetrics.class);

        assertThat(provider.measuredOperations(annotation))
            .isEqualTo(EnumSet.allOf(DalOperationType.class));
    }

    @Test
    void restrictedOperations_shouldMeasureOnlyThoseListed() {
        DalMetrics annotation = ReadOnlyDal.class.getAnnotation(DalMetrics.class);

        assertThat(provider.measuredOperations(annotation))
            .containsExactlyInAnyOrder(DalOperationType.READ, DalOperationType.READ_ONE);
    }

    @Test
    void getOrder_shouldBeMetricsPrecedence() {
        assertThat(provider.getOrder()).isEqualTo(DalInterceptorProvider.METRICS_PRECEDENCE);
    }

    private static DalInterceptionContext contextOf(Class<? extends Dal<?, ?>> dalClass) {
        return new DalInterceptionContext("testDal", dalClass);
    }

    abstract static class PlainDal implements Dal<Object, Long> {
    }

    @DalMetrics(enabled = false)
    abstract static class DisabledDal implements Dal<Object, Long> {
    }

    @DalMetrics
    abstract static class EnabledDal implements Dal<Object, Long> {
    }

    @DalMetrics(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
    abstract static class ReadOnlyDal implements Dal<Object, Long> {
    }
}
