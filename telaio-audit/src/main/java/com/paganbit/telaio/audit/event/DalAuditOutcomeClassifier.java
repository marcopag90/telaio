package com.paganbit.telaio.audit.event;

/**
 * Classifies a failure thrown by an audited DAL operation into a {@link DalAuditOutcome}.
 *
 * <p>Never consulted for successful operations. Provide a custom bean to map application-specific
 * exceptions to audit outcomes.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@FunctionalInterface
public interface DalAuditOutcomeClassifier {

    /**
     * Classifies the given failure.
     *
     * @param failure the exception thrown by the audited operation
     * @return the audit outcome to record
     */
    DalAuditOutcome classify(Throwable failure);
}
