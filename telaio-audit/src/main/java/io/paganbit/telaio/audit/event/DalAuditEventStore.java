package io.paganbit.telaio.audit.event;

/**
 * Persists {@link DalAuditEvent}s — the facade behind which audit storage backends plug in.
 *
 * <p>The default implementation writes to the application log; alternative backends (relational,
 * document stores, message brokers) only need to provide a bean of this type to replace it.</p>
 *
 * <p>{@code store} is called synchronously within the audited invocation, after the business
 * transaction has completed. Implementations should not throw errors: any exception is caught and logged
 * by the audit interceptors and never propagates to the business operation.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalAuditEventStore {

    /**
     * Persists the given audit event.
     *
     * @param event the event to persist
     */
    void store(DalAuditEvent event);
}
