package com.paganbit.telaio.rest.client.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

/**
 * {@code 404 Not Found} with a problem body: the target entity — or the DAL service itself —
 * does not exist. The server deliberately does not distinguish the two cases.
 *
 * <p>Raised by mutations ({@code update}, {@code delete}); {@code readOne} maps this condition
 * to an empty {@code Optional} instead.</p>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientNotFoundException extends DalClientResponseException {

    public DalClientNotFoundException(
        String message,
        int statusCode,
        @Nullable ProblemDetail problemDetail
    ) {
        super(message, statusCode, problemDetail);
    }
}
