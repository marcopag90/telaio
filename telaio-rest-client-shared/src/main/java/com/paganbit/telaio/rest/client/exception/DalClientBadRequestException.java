package com.paganbit.telaio.rest.client.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * {@code 400 Bad Request} without the {@code errors} problem extension — typically a malformed
 * {@code q} filter expression or invalid pagination parameters.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientBadRequestException extends DalClientResponseException {

    public DalClientBadRequestException(
        String message,
        int statusCode,
        @Nullable ProblemDetail problemDetail
    ) {
        super(message, statusCode, problemDetail);
    }
}
