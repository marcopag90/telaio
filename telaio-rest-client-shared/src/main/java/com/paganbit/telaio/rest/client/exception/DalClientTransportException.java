package com.paganbit.telaio.rest.client.exception;

/**
 * An I/O failure (connection refused, timeout, DNS, TLS, …) — no HTTP response was received
 * at all.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientTransportException extends DalClientException {

    public DalClientTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
