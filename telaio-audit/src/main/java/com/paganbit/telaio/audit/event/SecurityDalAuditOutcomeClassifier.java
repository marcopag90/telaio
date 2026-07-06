package com.paganbit.telaio.audit.event;

import org.springframework.security.access.AccessDeniedException;

/**
 * {@link DalAuditOutcomeClassifier} aware of Spring Security: authorization failures are
 * classified as {@link DalAuditOutcome#DENIED}; everything else follows the shared
 * {@link com.paganbit.telaio.core.exception.DalFailureKind} taxonomy (validation, not-found,
 * conflict, error) exactly like {@link DefaultDalAuditOutcomeClassifier}.
 *
 * <p>Telaio's own {@code DalAccessDeniedException} extends {@link AccessDeniedException}, so DAL
 * authorization failures are covered without a dependency on the security module.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class SecurityDalAuditOutcomeClassifier implements DalAuditOutcomeClassifier {

    private final DalAuditOutcomeClassifier fallback = new DefaultDalAuditOutcomeClassifier();

    @Override
    public DalAuditOutcome classify(Throwable failure) {
        return failure instanceof AccessDeniedException
            ? DalAuditOutcome.DENIED
            : fallback.classify(failure);
    }
}
