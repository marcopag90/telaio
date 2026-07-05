package io.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DalFailureKindTest {

    @Test
    void validationFailure_shouldBeValidation() {
        DalEntityValidationException failure = new DalEntityValidationException(List.of(
            new FieldError("product", "name", "", false, null, null, "must not be blank")));

        assertThat(DalFailureKind.of(failure)).isEqualTo(DalFailureKind.VALIDATION);
    }

    @Test
    void missingEntity_shouldBeNotFound() {
        assertThat(DalFailureKind.of(new DalEntityNotFoundException(Object.class, 42L)))
            .isEqualTo(DalFailureKind.NOT_FOUND);
    }

    @Test
    void optimisticLockFailure_shouldBeConflict() {
        assertThat(DalFailureKind.of(
            new OptimisticLockingFailureException("Row was updated by another transaction")))
            .isEqualTo(DalFailureKind.CONFLICT);
    }

    @Test
    void anyOtherFailure_shouldBeServerError() {
        assertThat(DalFailureKind.of(new RuntimeException("boom")))
            .isEqualTo(DalFailureKind.SERVER_ERROR);
        assertThat(DalFailureKind.of(new IllegalStateException("broken")))
            .isEqualTo(DalFailureKind.SERVER_ERROR);
    }

    @Test
    void isClientFault_shouldBeTrueForEverythingButServerError() {
        assertThat(DalFailureKind.VALIDATION.isClientFault()).isTrue();
        assertThat(DalFailureKind.NOT_FOUND.isClientFault()).isTrue();
        assertThat(DalFailureKind.CONFLICT.isClientFault()).isTrue();
        assertThat(DalFailureKind.SERVER_ERROR.isClientFault()).isFalse();
    }
}
