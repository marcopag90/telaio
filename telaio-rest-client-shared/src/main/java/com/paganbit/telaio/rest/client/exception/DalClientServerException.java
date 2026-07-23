package com.paganbit.telaio.rest.client.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * A {@code 5xx} response: the server failed to process the request. The body is deliberately
 * generic — the server never exposes internal error details.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientServerException extends DalClientResponseException {

    public DalClientServerException(
        String message,
        int statusCode,
        @Nullable ProblemDetail problemDetail
    ) {
        super(message, statusCode, problemDetail);
    }
}
