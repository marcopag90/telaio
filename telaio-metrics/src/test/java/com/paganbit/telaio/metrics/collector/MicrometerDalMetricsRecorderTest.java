package com.paganbit.telaio.metrics.collector;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.paganbit.telaio.core.adapter.DalOperationType;
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
        recorder.doRecord("customers", DalOperationType.CREATE, TimeUnit.MILLISECONDS.toNanos(5),
            DalMetricsOutcome.SUCCESS);

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
    void doRecord_shouldSeparateOutcomes() {
        recorder.doRecord("customers", DalOperationType.READ, 1_000, DalMetricsOutcome.SUCCESS);
        recorder.doRecord("customers", DalOperationType.READ, 1_000, DalMetricsOutcome.ERROR);
        recorder.doRecord("customers", DalOperationType.READ, 1_000, DalMetricsOutcome.CLIENT_ERROR);

        Timer success = registry.find("telaio.dal.operation")
            .tag("operation", "read").tag("outcome", "success").timer();
        Timer error = registry.find("telaio.dal.operation")
            .tag("operation", "read").tag("outcome", "error").timer();
        Timer clientError = registry.find("telaio.dal.operation")
            .tag("operation", "read").tag("outcome", "client_error").timer();
        assertThat(success).isNotNull();
        assertThat(error).isNotNull();
        assertThat(clientError).isNotNull();
        assertThat(success.count()).isEqualTo(1);
        assertThat(error.count()).isEqualTo(1);
        assertThat(clientError.count()).isEqualTo(1);
    }

    @Test
    void doRecord_shouldAccumulateRepeatedInvocationsIntoTheSameTimer() {
        recorder.doRecord("orders", DalOperationType.UPDATE, 2_000, DalMetricsOutcome.SUCCESS);
        recorder.doRecord("orders", DalOperationType.UPDATE, 4_000, DalMetricsOutcome.SUCCESS);

        Timer timer = registry.find("telaio.dal.operation")
            .tag("dal", "orders").tag("operation", "update").tag("outcome", "success").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
    }
}
