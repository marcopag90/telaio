package com.paganbit.telaio.audit.event;

import com.paganbit.telaio.core.exception.DalEntityNotFoundException;
import com.paganbit.telaio.core.exception.DalEntityValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;

import java.util.List;

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
    void defaultClassifier_withoutSecurityConcept_shouldClassifyAccessDeniedAsError() {
        assertThat(defaultClassifier.classify(new AccessDeniedException("forbidden")))
            .isEqualTo(DalAuditOutcome.ERROR);
        assertThat(defaultClassifier.classify(new RuntimeException("boom")))
            .isEqualTo(DalAuditOutcome.ERROR);
    }

    @Test
    void validationFailure_shouldBeClassifiedAsValidation_byBothClassifiers() {
        DalEntityValidationException failure = new DalEntityValidationException(List.of(
            new FieldError("product", "name", "", false, null, null, "must not be blank")));

        assertThat(defaultClassifier.classify(failure)).isEqualTo(DalAuditOutcome.VALIDATION);
        assertThat(securityClassifier.classify(failure)).isEqualTo(DalAuditOutcome.VALIDATION);
    }

    @Test
    void missingEntity_shouldBeClassifiedAsNotFound_byBothClassifiers() {
        DalEntityNotFoundException failure = new DalEntityNotFoundException(Object.class, 42L);

        assertThat(defaultClassifier.classify(failure)).isEqualTo(DalAuditOutcome.NOT_FOUND);
        assertThat(securityClassifier.classify(failure)).isEqualTo(DalAuditOutcome.NOT_FOUND);
    }

    @Test
    void optimisticLockFailure_shouldBeClassifiedAsConflict_byBothClassifiers() {
        OptimisticLockingFailureException failure =
            new OptimisticLockingFailureException("Row was updated by another transaction");

        assertThat(defaultClassifier.classify(failure)).isEqualTo(DalAuditOutcome.CONFLICT);
        assertThat(securityClassifier.classify(failure)).isEqualTo(DalAuditOutcome.CONFLICT);
    }
}
