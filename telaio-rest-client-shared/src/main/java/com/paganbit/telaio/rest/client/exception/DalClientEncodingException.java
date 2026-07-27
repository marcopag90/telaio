package com.paganbit.telaio.rest.client.exception;

/**
 * A request could not be encoded before being sent — e.g. the entity identifier could not be serialized
 * into its path-segment representation. This is a caller-side condition; no request reached the
 * server.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientEncodingException extends DalClientException {

    public DalClientEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
