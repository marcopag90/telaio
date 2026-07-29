package com.paganbit.telaio.rest.client.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * {@code 403 Forbidden}: the caller is authenticated but not allowed to perform the operation.
 *
 * <p>The problem body is deliberately bare (status and title only) — the server never reveals
 * why access was denied.</p>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
// S110: depth comes from the JDK exception chain
// Telaio adds only two deliberate levels (DalClientException, DalClientResponseException).
@SuppressWarnings("java:S110")
public class DalClientForbiddenException extends DalClientResponseException {

    public DalClientForbiddenException(
        String message,
        int statusCode,
        @Nullable ProblemDetail problemDetail
    ) {
        super(message, statusCode, problemDetail);
    }
}
