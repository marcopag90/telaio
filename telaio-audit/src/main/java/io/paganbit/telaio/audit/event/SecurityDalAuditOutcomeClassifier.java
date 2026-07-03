package io.paganbit.telaio.audit.event;

import org.springframework.security.access.AccessDeniedException;

/**
 * {@link DalAuditOutcomeClassifier} aware of Spring Security: authorization failures are
 * classified as {@link DalAuditOutcome#DENIED}, anything else as {@link DalAuditOutcome#ERROR}.
 *
 * <p>Telaio's own {@code DalAccessDeniedException} extends {@link AccessDeniedException}, so DAL
 * authorization failures are covered without a dependency on the security module.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class SecurityDalAuditOutcomeClassifier implements DalAuditOutcomeClassifier {

    @Override
    public DalAuditOutcome classify(Throwable failure) {
        return failure instanceof AccessDeniedException ? DalAuditOutcome.DENIED : DalAuditOutcome.ERROR;
    }
}
