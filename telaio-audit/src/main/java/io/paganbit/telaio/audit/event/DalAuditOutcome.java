package io.paganbit.telaio.audit.event;

/**
 * Classifies how an audited DAL operation ended.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public enum DalAuditOutcome {

    /**
     * The operation completed normally.
     */
    SUCCESS,

    /**
     * The attempt was rejected by an authorization check before reaching the DAL.
     */
    DENIED,

    /**
     * The attempt did not succeed for any other reason — this is intentionally broad and includes
     * validation failures and missing entities, not only server faults.
     */
    ERROR
}
