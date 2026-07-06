package com.paganbit.telaio.audit.event.format;

import com.paganbit.telaio.audit.event.DalAuditEvent;

/**
 * Renders a {@link DalAuditEvent} into the single-line string written to the application log by the
 * default logging store.
 *
 * <p>Implementations decide the wire format (for example, logfmt or JSON Lines). The produced string
 * must be a single line — the store writes it through one SLF4J call — and must never throw for
 * ordinary event content, so a malformed value can never break the audited operation. Implementations
 * must be thread-safe: the same formatter instance may be invoked concurrently from multiple request
 * threads.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@FunctionalInterface
public interface DalAuditEventFormatter {

    /**
     * Formats the given event as a single log line.
     *
     * @param event the audit event to render
     * @return the single-line representation to log
     */
    String format(DalAuditEvent event);
}
