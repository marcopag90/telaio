package io.paganbit.telaio.audit.event.format;

import io.paganbit.telaio.audit.event.DalAuditEvent;
import io.paganbit.telaio.audit.event.DalAuditOutcome;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Formats audit events as a single JSON object per line (JSON Lines).
 *
 * <p>The field schema is a stable contract downstream dashboards can rely on:</p>
 * <pre>{@code
 * {"timestamp":"2026-06-29T10:12:00Z","event":"dal.audit","dal":"article","operation":"READ_ONE",
 *  "outcome":"SUCCESS","principal":"alice","durationMs":12,"args":{"id":"5"}}
 * }</pre>
 *
 * <p>Field types (for building aggregator mappings): {@code timestamp}, {@code event}, {@code dal},
 * {@code operation}, {@code outcome} and {@code principal} are strings; {@code durationMs} is a number
 * (milliseconds); {@code args} and {@code mdc} are objects with string values; {@code principal} may be
 * {@code null}.</p>
 *
 * <p>{@code errorType}/{@code errorMessage} are present only when the outcome is not
 * {@link DalAuditOutcome#SUCCESS}, and an {@code mdc} object is present only when MDC inclusion is
 * enabled and the context is non-empty.</p>
 *
 * <p>Argument values are rendered defensively with {@code String.valueOf} rather than serialized
 * structurally: the snapshot may hold JPA entities whose deep serialization could trigger lazy
 * loading outside the transaction or produce recursive/oversized output. Arguments are kept under a
 * nested {@code args} object (for example {@code "args":{"id":"5"}}), queryable as {@code args.id} —
 * note this differs from the logfmt formatter, which emits flat {@code arg.<name>} fields.</p>
 *
 * <p>Serialization uses a dedicated, minimal {@link ObjectMapper} — never the application's bean —
 * so custom modules or serializers configured by the host cannot alter the contract or fail.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class JsonDalAuditEventFormatter implements DalAuditEventFormatter {

    private static final String EVENT_TYPE = "dal.audit";

    private final ObjectMapper mapper;
    private final boolean includeMdc;

    public JsonDalAuditEventFormatter(boolean includeMdc) {
        this(defaultMapper(), includeMdc);
    }

    public JsonDalAuditEventFormatter(ObjectMapper mapper, boolean includeMdc) {
        this.mapper = mapper;
        this.includeMdc = includeMdc;
    }

    @Override
    public String format(DalAuditEvent event) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("timestamp", event.timestamp().toString());
        json.put("event", EVENT_TYPE);
        json.put("dal", event.dalName());
        json.put("operation", String.valueOf(event.operation()));
        json.put("outcome", String.valueOf(event.outcome()));
        json.put("principal", event.principal());
        json.put("durationMs", event.duration().toMillis());
        json.put("args", renderArguments(event.arguments()));
        if (event.outcome() != DalAuditOutcome.SUCCESS) {
            json.put("errorType", event.errorType());
            json.put("errorMessage", event.errorMessage());
        }
        if (includeMdc) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            if (context != null && !context.isEmpty()) {
                json.put("mdc", new TreeMap<>(context));
            }
        }
        return mapper.writeValueAsString(json);
    }

    private static Map<String, String> renderArguments(Map<String, Object> arguments) {
        Map<String, String> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> arg : arguments.entrySet()) {
            rendered.put(arg.getKey(), String.valueOf(arg.getValue()));
        }
        return rendered;
    }

    private static ObjectMapper defaultMapper() {
        return JsonMapper.builder()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();
    }
}
