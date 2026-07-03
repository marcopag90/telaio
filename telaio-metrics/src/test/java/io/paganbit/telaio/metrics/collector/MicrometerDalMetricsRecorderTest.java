package io.paganbit.telaio.metrics.collector;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.paganbit.telaio.core.adapter.DalOperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerDalMetricsRecorderTest {

    private SimpleMeterRegistry registry;
    private MicrometerDalMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new MicrometerDalMetricsRecorder(registry, "telaio.dal.operation");
    }

    @Test
    void doRecord_shouldCreateTimerTaggedByDalOperationAndOutcome() {
        recorder.doRecord("customers", DalOperationType.CREATE, TimeUnit.MILLISECONDS.toNanos(5), false);

        Timer timer = registry.find("telaio.dal.operation")
            .tag("dal", "customers")
            .tag("operation", "create")
            .tag("outcome", "success")
            .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(5.0);
    }

    @Test
    void doRecord_shouldSeparateSuccessAndErrorOutcomes() {
        recorder.doRecord("customers", DalOperationType.READ, 1_000, false);
        recorder.doRecord("customers", DalOperationType.READ, 1_000, true);

        Timer success = registry.find("telaio.dal.operation")
            .tag("operation", "read").tag("outcome", "success").timer();
        Timer error = registry.find("telaio.dal.operation")
            .tag("operation", "read").tag("outcome", "error").timer();
        assertThat(success).isNotNull();
        assertThat(error).isNotNull();
        assertThat(success.count()).isEqualTo(1);
        assertThat(error.count()).isEqualTo(1);
    }

    @Test
    void doRecord_shouldAccumulateRepeatedInvocationsIntoTheSameTimer() {
        recorder.doRecord("orders", DalOperationType.UPDATE, 2_000, false);
        recorder.doRecord("orders", DalOperationType.UPDATE, 4_000, false);

        Timer timer = registry.find("telaio.dal.operation")
            .tag("dal", "orders").tag("operation", "update").tag("outcome", "success").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
    }
}
