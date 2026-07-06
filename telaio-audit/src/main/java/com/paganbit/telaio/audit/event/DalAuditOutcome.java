package com.paganbit.telaio.audit.event;

/**
 * Classifies how an audited DAL operation ended.
 *
 * <p>Client faults ({@link #VALIDATION}, {@link #NOT_FOUND}, {@link #CONFLICT}) are kept distinct
 * from {@link #ERROR} so the trail can separate misbehaving callers (bad payloads, id probing,
 * lost races) from genuine service faults. The values mirror
 * {@link com.paganbit.telaio.core.exception.DalFailureKind} plus the audit-specific
 * {@link #SUCCESS} and {@link #DENIED}.</p>
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
     * The request payload failed validation (a client fault, maps to HTTP {@code 400}).
     */
    VALIDATION,

    /**
     * The target entity does not exist or is hidden by the DAL's default filter (a client
     * fault, maps to HTTP {@code 404}).
     */
    NOT_FOUND,

    /**
     * A versioned entity was modified concurrently (a client-retryable fault, maps to HTTP
     * {@code 409}).
     */
    CONFLICT,

    /**
     * The attempt failed for any other reason: the service, not the caller, is at fault.
     */
    ERROR
}
