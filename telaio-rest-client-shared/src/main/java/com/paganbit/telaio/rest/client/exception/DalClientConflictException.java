package com.paganbit.telaio.rest.client.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * {@code 409 Conflict}: a versioned entity was modified concurrently between its load and the
 * write or remove that followed. Re-read the resource and retry the operation.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientConflictException extends DalClientResponseException {

    public DalClientConflictException(
        String message,
        int statusCode,
        @Nullable ProblemDetail problemDetail
    ) {
        super(message, statusCode, problemDetail);
    }
}
