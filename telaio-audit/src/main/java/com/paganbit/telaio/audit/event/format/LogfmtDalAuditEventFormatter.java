package com.paganbit.telaio.audit.event.format;

import com.paganbit.telaio.audit.event.DalAuditEvent;
import com.paganbit.telaio.audit.event.DalAuditOutcome;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

import java.util.Map;
import java.util.TreeMap;

/**
 * Formats audit events as a robust {@code key=value} (logfmt) single line.
 *
 * <p>Every value is quoted and escaped when it contains a space, {@code =}, a quote, a backslash, or
 * a control character, so a value can never spill into the next field — keeping the line parseable by
 * the automatic key/value extractors of log aggregators. The argument snapshot is flattened
 * into {@code arg.<name>} fields rather than emitted as a single {@code Map.toString()} token,
 * and the event timestamp is rendered explicitly in ISO-8601.</p>
 *
 * <p>When MDC inclusion is enabled, the current context entries are appended as sorted
 * {@code mdc.<key>} fields for request correlation.</p>
 *
 * <p>Keys are written verbatim (not escaped): argument keys are the fixed parameter roles
 * ({@code input}/{@code id}/{@code patch}/{@code filter}/{@code pageable}) and MDC keys are
 * caller-controlled, so no key contains a logfmt delimiter. Control characters other than
 * {@code \n}/{@code \r}/{@code \t} are replaced by a single space (lossy) to preserve one-line
 * output.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class LogfmtDalAuditEventFormatter implements DalAuditEventFormatter {

    private static final char SPACE = ' ';

    private final boolean includeMdc;

    public LogfmtDalAuditEventFormatter(boolean includeMdc) {
        this.includeMdc = includeMdc;
    }

    @Override
    public String format(DalAuditEvent event) {
        StringBuilder line = new StringBuilder();
        append(line, "timestamp", event.timestamp());
        append(line, "dal", event.dalName());
        append(line, "operation", event.operation());
        append(line, "outcome", event.outcome());
        append(line, "principal", event.principal());
        append(line, "durationMs", event.duration().toMillis());
        for (Map.Entry<String, Object> arg : event.arguments().entrySet()) {
            append(line, "arg." + arg.getKey(), arg.getValue());
        }
        if (event.outcome() != DalAuditOutcome.SUCCESS) {
            append(line, "errorType", event.errorType());
            append(line, "errorMessage", event.errorMessage());
        }
        if (includeMdc) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            if (context != null && !context.isEmpty()) {
                for (Map.Entry<String, String> entry : new TreeMap<>(context).entrySet()) {
                    append(line, "mdc." + entry.getKey(), entry.getValue());
                }
            }
        }
        return line.toString();
    }

    private static void append(StringBuilder line, String key, @Nullable Object rawValue) {
        if (!line.isEmpty()) {
            line.append(SPACE);
        }
        line.append(key).append('=').append(renderValue(rawValue));
    }

    private static String renderValue(@Nullable Object rawValue) {
        if (rawValue == null) {
            return "null";
        }
        String value = String.valueOf(rawValue);
        return needsQuoting(value) ? '"' + escape(value) + '"' : value;
    }

    private static boolean needsQuoting(String value) {
        if (value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == SPACE || c == '=' || c == '"' || c == '\\' || c < SPACE) {
                return true;
            }
        }
        return false;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c < SPACE ? SPACE : c);
            }
        }
        return escaped.toString();
    }
}
