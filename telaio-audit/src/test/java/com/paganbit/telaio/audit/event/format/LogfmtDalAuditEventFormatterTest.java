package com.paganbit.telaio.audit.event.format;

import com.paganbit.telaio.audit.event.DalAuditEvent;
import com.paganbit.telaio.audit.event.DalAuditOutcome;
import com.paganbit.telaio.core.adapter.DalOperationType;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogfmtDalAuditEventFormatterTest {

    private static final Instant TS = Instant.parse("2026-06-12T10:00:00Z");

    private final LogfmtDalAuditEventFormatter formatter = new LogfmtDalAuditEventFormatter(false);

    @Test
    void successfulEvent_shouldRenderFlattenedArgsAndNoErrorFields() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "products", DalOperationType.CREATE, "admin",
            Map.of("input", Map.of("name", "Widget")), DalAuditOutcome.SUCCESS, null, null,
            Duration.ofMillis(12));

        assertThat(formatter.format(event)).isEqualTo(
            "timestamp=2026-06-12T10:00:00Z dal=products operation=CREATE outcome=SUCCESS"
                + " principal=admin durationMs=12 arg.input=\"{name=Widget}\"");
    }

    @Test
    void failedEvent_shouldIncludeErrorFields() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "products", DalOperationType.DELETE, "user", Map.of("id", 5L),
            DalAuditOutcome.DENIED, "java.lang.SecurityException", "forbidden", Duration.ofMillis(3));

        assertThat(formatter.format(event)).isEqualTo(
            "timestamp=2026-06-12T10:00:00Z dal=products operation=DELETE outcome=DENIED"
                + " principal=user durationMs=3 arg.id=5"
                + " errorType=java.lang.SecurityException errorMessage=forbidden");
    }

    @Test
    void valuesWithDelimiters_shouldBeQuotedAndEscaped() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("note", "a \"quoted\" line\nwith breaks\tand\ttabs");
        DalAuditEvent event = new DalAuditEvent(
            TS, "my dal", DalOperationType.CREATE, "john doe", args, DalAuditOutcome.SUCCESS,
            null, null, Duration.ofMillis(1));

        String line = formatter.format(event);

        assertThat(line)
            .contains("dal=\"my dal\"")
            .contains("principal=\"john doe\"")
            .contains("arg.note=\"a \\\"quoted\\\" line\\nwith breaks\\tand\\ttabs\"")
            .doesNotContain("\n", "\r", "\t");
    }

    @Test
    void nullPrincipal_shouldRenderLiteralNull() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "products", DalOperationType.READ_ONE, null, Map.of("id", 1L),
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(1));

        assertThat(formatter.format(event)).contains("principal=null");
    }

    @Test
    void includeMdc_shouldAppendSortedMdcFields() {
        LogfmtDalAuditEventFormatter withMdc = new LogfmtDalAuditEventFormatter(true);
        DalAuditEvent event = new DalAuditEvent(
            TS, "products", DalOperationType.READ_ONE, "admin", Map.of("id", 1L),
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(1));

        MDC.put("traceId", "abc123");
        MDC.put("spanId", "def456");
        try {
            assertThat(withMdc.format(event))
                .endsWith("mdc.spanId=def456 mdc.traceId=abc123");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void includeMdcDisabled_shouldOmitMdcFields() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "products", DalOperationType.READ_ONE, "admin", Map.of("id", 1L),
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(1));

        MDC.put("traceId", "abc123");
        try {
            assertThat(formatter.format(event)).doesNotContain("mdc.");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void emptyStringValue_shouldRenderQuotedEmpty() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "products", DalOperationType.CREATE, "admin", Map.of("input", ""),
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(1));

        assertThat(formatter.format(event)).contains("arg.input=\"\"");
    }

    @Test
    void controlCharValue_shouldBeReplacedBySpace() {
        // Build the control character in code (no literal/escape in source) so it survives intact.
        String control = String.valueOf((char) 1);
        DalAuditEvent event = new DalAuditEvent(
            TS, "products", DalOperationType.CREATE, "admin", Map.of("input", "a" + control + "b"),
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(1));

        String line = formatter.format(event);

        assertThat(line)
            .contains("arg.input=\"a b\"")
            .doesNotContain(control);
    }

    @Test
    void nullArgValue_shouldRenderLiteralNull() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", null);
        DalAuditEvent event = new DalAuditEvent(
            TS, "products", DalOperationType.READ_ONE, "admin", args,
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(1));

        assertThat(formatter.format(event)).contains("arg.id=null");
    }
}
