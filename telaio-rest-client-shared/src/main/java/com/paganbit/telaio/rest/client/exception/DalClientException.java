package com.paganbit.telaio.rest.client.exception;

import org.jspecify.annotations.Nullable;

/**
 * Root of the Telaio client exception hierarchy.
 *
 * <p>Carries no HTTP state on purpose: an HTTP status only exists when a response was received,
 * which is the invariant of {@link DalClientResponseException}.</p>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public abstract class DalClientException extends RuntimeException {

    protected DalClientException(String message) {
        super(message);
    }

    protected DalClientException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
