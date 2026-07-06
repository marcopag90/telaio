package com.paganbit.telaio.metrics.collector;

/**
 * Outcome of a measured DAL invocation.
 *
 * <p>Client faults (validation, not-found, optimistic-lock conflicts — see
 * {@link com.paganbit.telaio.core.exception.DalFailureKind}) are counted apart from genuine
 * service errors, so the error rate reflects service health rather than caller behavior, while
 * misbehaving callers (bad payloads, id probing) remain visible. Authorization denials never
 * reach the {@code Dal} bean and therefore have no outcome here.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public enum DalMetricsOutcome {

    /**
     * The invocation completed normally.
     */
    SUCCESS,

    /**
     * The invocation failed because of the caller: validation failure, missing/hidden entity,
     * or a concurrent-modification conflict.
     */
    CLIENT_ERROR,

    /**
     * The invocation failed because of the service.
     */
    ERROR
}
