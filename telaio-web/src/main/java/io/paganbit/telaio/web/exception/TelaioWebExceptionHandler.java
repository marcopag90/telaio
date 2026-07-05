package io.paganbit.telaio.web.exception;

import io.paganbit.telaio.core.exception.DalEntityNotFoundException;
import io.paganbit.telaio.core.exception.DalEntityValidationException;
import io.paganbit.telaio.core.exception.DalNotFoundException;
import io.paganbit.telaio.core.exception.DalRegistryException;
import io.paganbit.telaio.web.validation.ValidationError;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates the DAL exceptions that surface at the REST boundary into RFC 9457 {@link ProblemDetail}
 * responses ({@code application/problem+json}) — the single, framework-native error standard for this API.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@RestControllerAdvice
@Order(1000)
@Hidden
public class TelaioWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TelaioWebExceptionHandler.class);

    @ExceptionHandler(DalEntityValidationException.class)
    ProblemDetail handleEntityValidationException(DalEntityValidationException ex) {
        // Client error: keep it off the default log level but leave an on-demand trace (field names only,
        // never the rejected values, which may be sensitive).
        log.debug("Request validation failed ({} field error(s): {})",
            ex.getErrors().size(), ex.getErrors().stream().map(FieldError::getField).toList());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", toValidationErrors(ex.getErrors()));
        return problem;
    }

    /**
     * Maps a missing entity or resource to {@code 404 Not Found}.
     *
     * <p>{@link DalNotFoundException} is matched here (rather than by the broader
     * {@link DalRegistryException} handler below) because, although it extends
     * {@code DalRegistryException}, a missing DAL service is a not-found condition, not an
     * internal error. Spring resolves the most specific {@code @ExceptionHandler}, so a
     * {@code DalNotFoundException} lands here while any other {@code DalRegistryException}
     * falls through to the {@code 500} handler.</p>
     */
    @ExceptionHandler({
        DalEntityNotFoundException.class,
        DalNotFoundException.class,
        DalResourceNotFoundException.class
    })
    ProblemDetail handleNotFound(RuntimeException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Maps an optimistic-locking failure to {@code 409 Conflict}. Raised when a versioned entity
     * (JPA {@code @Version}) is modified concurrently between its load and the write or remove operation
     * that follows — the client should re-read the resource and retry. The body stays generic;
     * the conflicting state is never exposed.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        log.debug("Concurrent modification detected: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "The resource was modified concurrently; re-read and retry");
    }

    /**
     * Maps a DAL registry resolution failure (other than {@link DalNotFoundException}) to
     * {@code 500 Internal Server Error}. The internal cause is logged, never exposed in the body.
     */
    @ExceptionHandler(DalRegistryException.class)
    ProblemDetail handleRegistryException(DalRegistryException ex) {
        log.warn("DAL registry resolution failed", ex);
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /**
     * Maps a non-exposed operation to {@code 405 Method Not Allowed} (with an {@code Allow} header listing
     * the operations the target URI still exposes) or, when the URI exposes nothing, to
     * {@code 404 Not Found} so the URI's existence is not revealed. Both intentionally carry an empty body
     * (the {@code 404} variant must not hint that the operation exists), so this branch does not use
     * {@link ProblemDetail}.
     */
    @ExceptionHandler(DalOperationNotExposedException.class)
    ResponseEntity<Void> handleOperationNotExposed(DalOperationNotExposedException ex) {
        log.debug("Operation not exposed (attempted={}, uriHasNoExposedOperations={})",
            ex.attemptedOperation(), ex.uriHasNoExposedOperations());
        if (ex.uriHasNoExposedOperations()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .allow(ex.allowedMethods().toArray(HttpMethod[]::new))
            .build();
    }

    private List<ValidationError> toValidationErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
            .map(fieldError -> new ValidationError(
                fieldError.getObjectName(),
                fieldError.getField(),
                fieldError.getRejectedValue(),
                fieldError.getDefaultMessage()))
            .toList();
    }
}
