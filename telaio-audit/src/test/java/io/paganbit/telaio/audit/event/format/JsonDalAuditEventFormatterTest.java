package io.paganbit.telaio.audit.event.format;

import io.paganbit.telaio.audit.event.DalAuditEvent;
import io.paganbit.telaio.audit.event.DalAuditOutcome;
import io.paganbit.telaio.core.adapter.DalOperationType;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDalAuditEventFormatterTest {

    private static final Instant TS = Instant.parse("2026-06-12T10:00:00Z");

    private final JsonDalAuditEventFormatter formatter = new JsonDalAuditEventFormatter(false);
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        assertThat(json).doesNotContain("\n");
        return mapper.readValue(json, Map.class);
    }

    @Test
    void successfulEvent_shouldEmitTypedFieldsAndStructuredArgs() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "article", DalOperationType.READ_ONE, "alice", Map.of("id", 5L),
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(12));

        Map<String, Object> json = parse(formatter.format(event));

        assertThat(json)
            .containsEntry("timestamp", "2026-06-12T10:00:00Z")
            .containsEntry("event", "dal.audit")
            .containsEntry("dal", "article")
            .containsEntry("operation", "READ_ONE")
            .containsEntry("outcome", "SUCCESS")
            .containsEntry("principal", "alice")
            .containsEntry("args", Map.of("id", "5"))
            .doesNotContainKeys("errorType", "errorMessage", "mdc");

        assertThat(json.get("durationMs")).isInstanceOf(Number.class);
        assertThat(((Number) json.get("durationMs")).longValue()).isEqualTo(12L);
    }

    @Test
    void failedEvent_shouldIncludeErrorFields() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "article", DalOperationType.DELETE, "bob", Map.of("id", 9L),
            DalAuditOutcome.ERROR, "java.lang.IllegalStateException", "boom", Duration.ofMillis(4));

        Map<String, Object> json = parse(formatter.format(event));

        assertThat(json)
            .containsEntry("outcome", "ERROR")
            .containsEntry("errorType", "java.lang.IllegalStateException")
            .containsEntry("errorMessage", "boom");
    }

    @Test
    void valuesWithSpecialCharacters_shouldStayValidJson() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "article", DalOperationType.CREATE, "a \"weird\"\nname",
            Map.of("input", "{title=He said \"hi\"}"), DalAuditOutcome.SUCCESS, null, null,
            Duration.ofMillis(1));

        Map<String, Object> json = parse(formatter.format(event));

        assertThat(json)
            .containsEntry("principal", "a \"weird\"\nname")
            .containsEntry("args", Map.of("input", "{title=He said \"hi\"}"));
    }

    @Test
    void nullPrincipal_shouldSerializeAsJsonNull() {
        DalAuditEvent event = new DalAuditEvent(
            TS, "article", DalOperationType.READ_ONE, null, Map.of("id", 1L),
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(1));

        Map<String, Object> json = parse(formatter.format(event));

        assertThat(json).containsKey("principal");
        assertThat(json.get("principal")).isNull();
    }

    @Test
    void includeMdc_shouldEmitMdcObjectWhenContextPresent() {
        JsonDalAuditEventFormatter withMdc = new JsonDalAuditEventFormatter(true);
        DalAuditEvent event = new DalAuditEvent(
            TS, "article", DalOperationType.READ_ONE, "alice", Map.of("id", 1L),
            DalAuditOutcome.SUCCESS, null, null, Duration.ofMillis(1));

        MDC.put("traceId", "abc123");
        try {
            Map<String, Object> json = parse(withMdc.format(event));
            assertThat(json).containsEntry("mdc", Map.of("traceId", "abc123"));
        } finally {
            MDC.clear();
        }
    }
}
