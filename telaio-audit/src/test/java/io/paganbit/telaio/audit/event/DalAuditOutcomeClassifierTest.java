package io.paganbit.telaio.audit.event;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class DalAuditOutcomeClassifierTest {

    private final SecurityDalAuditOutcomeClassifier securityClassifier =
        new SecurityDalAuditOutcomeClassifier();
    private final DefaultDalAuditOutcomeClassifier defaultClassifier =
        new DefaultDalAuditOutcomeClassifier();

    @Test
    void accessDenied_shouldBeClassifiedAsDenied() {
        assertThat(securityClassifier.classify(new AccessDeniedException("forbidden")))
            .isEqualTo(DalAuditOutcome.DENIED);
    }

    @Test
    void accessDeniedSubclass_shouldBeClassifiedAsDenied() {
        // Telaio's DalAccessDeniedException extends AccessDeniedException just like this stub
        AccessDeniedException subclass = new AccessDeniedException("forbidden") {
        };

        assertThat(securityClassifier.classify(subclass)).isEqualTo(DalAuditOutcome.DENIED);
    }

    @Test
    void otherFailures_shouldBeClassifiedAsError() {
        assertThat(securityClassifier.classify(new RuntimeException("boom")))
            .isEqualTo(DalAuditOutcome.ERROR);
    }

    @Test
    void defaultClassifier_shouldAlwaysClassifyAsError() {
        assertThat(defaultClassifier.classify(new AccessDeniedException("forbidden")))
            .isEqualTo(DalAuditOutcome.ERROR);
        assertThat(defaultClassifier.classify(new RuntimeException("boom")))
            .isEqualTo(DalAuditOutcome.ERROR);
    }
}
