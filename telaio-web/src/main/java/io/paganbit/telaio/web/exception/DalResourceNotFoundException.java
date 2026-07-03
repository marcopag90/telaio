package io.paganbit.telaio.web.exception;

/**
 * Exception thrown when a requested resource is not found.
 * Mapped to a {@code 404 Not Found} HTTP status code by {@link TelaioWebExceptionHandler}.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalResourceNotFoundException extends RuntimeException {

    public DalResourceNotFoundException(Object id) {
        super("Resource not found with ID: " + id);
    }
}
