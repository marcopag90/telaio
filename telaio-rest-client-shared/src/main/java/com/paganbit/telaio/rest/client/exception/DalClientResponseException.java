package com.paganbit.telaio.rest.client.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * A non-2xx HTTP response from the remote DAL API. Thrown directly for statuses outside the
 * server's documented error contract; the documented statuses raise the dedicated subclasses.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientResponseException extends DalClientException {

    private final int statusCode;
    private final transient @Nullable ProblemDetail problemDetail;

    public DalClientResponseException(
        String message,
        int statusCode,
        @Nullable ProblemDetail problemDetail
    ) {
        super(message);
        this.statusCode = statusCode;
        this.problemDetail = problemDetail;
    }

    /** The HTTP status code of the response; always present. */
    public int statusCode() {
        return statusCode;
    }

    /**
     * The RFC 9457 problem body of the response, or {@code null} when the response carried no
     * {@code application/problem+json} body (e.g. the deliberately bodyless not-exposed
     * responses).
     */
    public @Nullable ProblemDetail problemDetail() {
        return problemDetail;
    }
}
