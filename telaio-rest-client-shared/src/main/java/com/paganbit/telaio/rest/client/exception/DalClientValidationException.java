package com.paganbit.telaio.rest.client.exception;

import com.paganbit.telaio.rest.contract.v1.ValidationError;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

import java.util.List;

/**
 * {@code 400 Bad Request} carrying the {@code errors} problem extension: the submitted payload
 * failed the server-side entity validation.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientValidationException extends DalClientResponseException {

    private final transient List<ValidationError> errors;

    public DalClientValidationException(
        String message,
        int statusCode,
        @Nullable ProblemDetail problemDetail,
        List<ValidationError> errors
    ) {
        super(message, statusCode, problemDetail);
        this.errors = List.copyOf(errors);
    }

    /** The field-level validation failures; never {@code null}, possibly empty. */
    public List<ValidationError> errors() {
        return errors;
    }
}
