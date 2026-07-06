package com.paganbit.telaio.audit.event;

import com.paganbit.telaio.audit.event.format.DalAuditEventFormatter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link DalAuditEventStore} that writes each event to the application log as a single line,
 * delegating its serialization to a {@link DalAuditEventFormatter} (logfmt or JSON Lines).
 *
 * <p>Successful operations are logged at {@code INFO}; denied and failed ones at {@code WARN}. Events
 * are emitted under a dedicated, configurable logger category (default
 * {@code com.paganbit.telaio.audit.AUDIT}) so the host application can route auditing to its own
 * appender — a separate file, index, or sourcetype — independently of its other logs.</p>
 *
 * <p>A failing formatter never propagates: the store catches it and emits, at {@code WARN}, a minimal
 * fallback line of the form
 * {@code dal=<name> operation=<op> outcome=<outcome> [audit formatting failed: <exception>]} so the
 * audited operation stays observable, then returns. This holds the {@code DalAuditEventStore} "should
 * not throw" contract at the store boundary itself, independently of any outer interceptor guard.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class LoggingDalAuditEventStore implements DalAuditEventStore {

    private final Logger log;
    private final DalAuditEventFormatter formatter;

    /**
     * Creates a store that logs formatted events under the given SLF4J logger category.
     *
     * @param formatter renders each event into its single-line form (logfmt or JSON Lines)
     * @param category  the logger category events are emitted under — see
     *                  {@code telaio.audit.logging.category} (default {@code com.paganbit.telaio.audit.AUDIT}),
     *                  which the host routes to its own appender
     */
    public LoggingDalAuditEventStore(DalAuditEventFormatter formatter, String category) {
        this.formatter = formatter;
        this.log = LoggerFactory.getLogger(category);
    }

    @Override
    public void store(DalAuditEvent event) {
        if (event.outcome() == DalAuditOutcome.SUCCESS) {
            if (log.isInfoEnabled()) {
                String line = formatSafely(event);
                if (line != null) {
                    log.info(line);
                }
            }
        } else if (log.isWarnEnabled()) {
            String line = formatSafely(event);
            if (line != null) {
                log.warn(line);
            }
        }
    }

    private @Nullable String formatSafely(DalAuditEvent event) {
        try {
            return formatter.format(event);
        } catch (Exception e) {
            log.warn("dal={} operation={} outcome={} [audit formatting failed: {}]",
                event.dalName(), event.operation(), event.outcome(), e.toString());
            return null;
        }
    }
}
