package io.paganbit.telaio.core.exception;

import org.springframework.validation.FieldError;

import java.util.List;

/**
 * Exception thrown when validation fails in the Telaio Data Access Layer.
 * Contains a list of validation errors that occurred during validation.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalEntityValidationException extends RuntimeException {

    private final List<FieldError> errors;

    /**
     * Returns the list of validation errors that caused this exception.
     *
     * @return a list of Spring {@link FieldError} instances containing validation error details
     */
    public List<FieldError> getErrors() {
        return errors;
    }

    /**
     * Constructs a new DalValidatorException with the specified validation errors.
     *
     * @param errors a list of Spring {@link FieldError} instances containing validation error details
     */
    public DalEntityValidationException(List<FieldError> errors) {
        this.errors = errors;
    }
}
