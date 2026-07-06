package com.paganbit.telaio.security.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * Exception thrown when a DAL operation is not authorized by the configured
 * {@link com.paganbit.telaio.security.adapter.DalAuthAdapter}.
 *
 * <p>This exception extends Spring's {@link AccessDeniedException} to allow
 * seamless integration with Spring Security, while providing DAL-specific
 * semantics for better error handling and diagnostics.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalAccessDeniedException extends AccessDeniedException {

    /**
     * Constructs a new {@code DalAccessDeniedException} with the specified detail message.
     *
     * @param msg the detail message
     */
    public DalAccessDeniedException(String msg) {
        super(msg);
    }

    /**
     * Constructs a new {@code DalAccessDeniedException} with the specified detail message and cause.
     *
     * @param msg   the detail message
     * @param cause the root cause (usually from the auth adapter)
     */
    public DalAccessDeniedException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
