package io.paganbit.telaio.audit.event;

/**
 * Fallback {@link DalAuditOutcomeClassifier} that classifies every failure as
 * {@link DalAuditOutcome#ERROR}.
 *
 * <p>Used when Spring Security is not on the classpath and no authorization concept exists.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultDalAuditOutcomeClassifier implements DalAuditOutcomeClassifier {

    @Override
    public DalAuditOutcome classify(Throwable failure) {
        return DalAuditOutcome.ERROR;
    }
}
