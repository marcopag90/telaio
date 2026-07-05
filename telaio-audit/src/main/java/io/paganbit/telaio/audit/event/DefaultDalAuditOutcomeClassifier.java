package io.paganbit.telaio.audit.event;

import io.paganbit.telaio.core.exception.DalFailureKind;

/**
 * Default {@link DalAuditOutcomeClassifier}: maps the shared {@link DalFailureKind} taxonomy to
 * audit outcomes, so client faults (validation, not-found, conflict) are distinguished from
 * genuine service errors.
 *
 * <p>Used when Spring Security is not on the classpath and no authorization concept exists.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultDalAuditOutcomeClassifier implements DalAuditOutcomeClassifier {

    @Override
    public DalAuditOutcome classify(Throwable failure) {
        return switch (DalFailureKind.of(failure)) {
            case VALIDATION -> DalAuditOutcome.VALIDATION;
            case NOT_FOUND -> DalAuditOutcome.NOT_FOUND;
            case CONFLICT -> DalAuditOutcome.CONFLICT;
            case SERVER_ERROR -> DalAuditOutcome.ERROR;
        };
    }
}
