package com.paganbit.telaio.web.exception;

import com.paganbit.telaio.core.adapter.DalOperationType;
import org.springframework.http.HttpMethod;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Raised when a request targets a CRUD operation a DAL does not expose on the remote boundary (see
 * {@code @DalService(operations = ...)}).
 *
 * <p>It is a <em>structural</em> rejection, distinct from an authorization denial: the operation has no
 * endpoint at all, rather than being forbidden to a particular principal. It is mapped to:</p>
 * <ul>
 *   <li>{@code 405 Method Not Allowed} (with an {@code Allow} header listing {@link #allowedMethods()})
 *       when the same URI still exposes other operations, or</li>
 *   <li>{@code 404 Not Found} when no operation on that URI is exposed (the URI does not exist) —
 *       indicated by {@link #uriHasNoExposedOperations()}.</li>
 * </ul>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalOperationNotExposedException extends RuntimeException {

    private final transient DalOperationType attemptedOperation;
    private final transient Set<HttpMethod> allowedMethods;
    private final boolean uriHasNoExposedOperations;

    public DalOperationNotExposedException(
        String dalName,
        DalOperationType attemptedOperation,
        Set<HttpMethod> allowedMethods,
        boolean uriHasNoExposedOperations
    ) {
        super("Operation %s is not exposed by DAL '%s'".formatted(attemptedOperation, dalName));
        this.attemptedOperation = attemptedOperation;
        this.allowedMethods = Collections.unmodifiableSet(new LinkedHashSet<>(allowedMethods));
        this.uriHasNoExposedOperations = uriHasNoExposedOperations;
    }

    public DalOperationType attemptedOperation() {
        return attemptedOperation;
    }

    /**
     * The HTTP methods still exposed on the target URI, for the {@code Allow} header of a {@code 405}
     * response. Empty when {@link #uriHasNoExposedOperations()} is {@code true}.
     */
    public Set<HttpMethod> allowedMethods() {
        return allowedMethods;
    }

    /**
     * Whether the target URI exposes no operation at all (so the response should be {@code 404} rather
     * than {@code 405}).
     */
    public boolean uriHasNoExposedOperations() {
        return uriHasNoExposedOperations;
    }
}
