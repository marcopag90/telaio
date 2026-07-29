package com.paganbit.telaio.rest.client.exception;

/**
 * A 2xx response whose body does not match the DAL API v1 wire shape (e.g. a page response
 * without the {@code content}/{@code page} members, or a create response with no entity body) —
 * typically a misrouted base URL or a non-Telaio endpoint answering in the server's place.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientMalformedResponseException extends DalClientException {

    public DalClientMalformedResponseException(String message) {
        super(message);
    }

    public DalClientMalformedResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
