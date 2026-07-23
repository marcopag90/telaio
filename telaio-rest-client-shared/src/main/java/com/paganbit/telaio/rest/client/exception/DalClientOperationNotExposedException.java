package com.paganbit.telaio.rest.client.exception;

import org.springframework.http.HttpMethod;

import java.util.Set;

/**
 * The attempted operation is not exposed by the target DAL: a deliberately bodyless
 * {@code 405 Method Not Allowed} (with the {@code Allow} header listing what the URI still
 * exposes) or, when the URI exposes nothing at all, a bodyless {@code 404 Not Found}.
 *
 * <p>This is a configuration mismatch between client and server (the DAL's {@code operations}
 * exposure), not a data condition — which is why it is never mapped to an empty result.</p>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalClientOperationNotExposedException extends DalClientResponseException {

    private final transient Set<HttpMethod> allowedMethods;

    public DalClientOperationNotExposedException(
        String message,
        int statusCode,
        Set<HttpMethod> allowedMethods
    ) {
        super(message, statusCode, null);
        this.allowedMethods = Set.copyOf(allowedMethods);
    }

    /**
     * The HTTP methods the target URI still exposes (from the {@code Allow} header); empty —
     * never {@code null} — for the bodyless-404 case where the URI exposes nothing.
     */
    public Set<HttpMethod> allowedMethods() {
        return allowedMethods;
    }
}
