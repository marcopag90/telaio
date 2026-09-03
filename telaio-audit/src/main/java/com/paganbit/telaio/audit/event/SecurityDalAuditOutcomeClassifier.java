package com.paganbit.telaio.audit.event;

import com.paganbit.telaio.core.exception.DalFilterFieldNotReadableException;
import com.paganbit.telaio.core.exception.DalSortFieldNotReadableException;
import org.springframework.security.access.AccessDeniedException;

/**
 * {@link DalAuditOutcomeClassifier} aware of Spring Security: authorization failures are
 * classified as {@link DalAuditOutcome#DENIED}; everything else follows the shared
 * {@link com.paganbit.telaio.core.exception.DalFailureKind DalFailureKind} taxonomy (validation, not-found,
 * conflict, error) exactly like {@link DefaultDalAuditOutcomeClassifier}.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class SecurityDalAuditOutcomeClassifier implements DalAuditOutcomeClassifier {

    private final DalAuditOutcomeClassifier fallback = new DefaultDalAuditOutcomeClassifier();

    @Override
    public DalAuditOutcome classify(Throwable failure) {
        return failure instanceof AccessDeniedException
            || failure instanceof DalFilterFieldNotReadableException
            || failure instanceof DalSortFieldNotReadableException
            ? DalAuditOutcome.DENIED
            : fallback.classify(failure);
    }
}
