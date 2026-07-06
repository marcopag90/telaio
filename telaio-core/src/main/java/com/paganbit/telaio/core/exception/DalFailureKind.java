package com.paganbit.telaio.core.exception;

import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Classifies a failure raised by a {@link com.paganbit.telaio.core.Dal} operation as a
 * <em>client fault</em> (the caller's request cannot be satisfied as-is) or a
 * <em>server error</em> (the service itself misbehaved).
 *
 * <p>This is the single, shared taxonomy consumed by the cross-cutting modules (audit, metrics)
 * so that client-caused failures do not inflate error rates or pollute audit trails as service
 * faults. The kinds mirror the HTTP statuses telaio-web maps the same exceptions to:</p>
 *
 * <ul>
 *   <li>{@link #VALIDATION} — the payload failed validation ({@code 400});</li>
 *   <li>{@link #NOT_FOUND} — the target entity does not exist or is hidden by the DAL's
 *       default filter ({@code 404});</li>
 *   <li>{@link #CONFLICT} — a versioned entity was modified concurrently ({@code 409});</li>
 *   <li>{@link #SERVER_ERROR} — anything else ({@code 500}).</li>
 * </ul>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public enum DalFailureKind {

    /**
     * The request payload failed validation ({@link DalEntityValidationException}).
     */
    VALIDATION,

    /**
     * The target entity does not exist — or is hidden by the DAL's default filter, which is
     * indistinguishable by design ({@link DalEntityNotFoundException}).
     */
    NOT_FOUND,

    /**
     * A versioned entity was modified concurrently between load and write/removal
     * (Spring's {@link OptimisticLockingFailureException}).
     */
    CONFLICT,

    /**
     * Any other failure: the service, not the caller, is at fault.
     */
    SERVER_ERROR;

    /**
     * Resolves the kind of the given failure.
     *
     * @param failure the failure raised by a DAL operation
     * @return the matching kind, {@link #SERVER_ERROR} when the failure is none of the
     * recognized client faults
     */
    public static DalFailureKind of(Throwable failure) {
        if (failure instanceof DalEntityValidationException) {
            return VALIDATION;
        }
        if (failure instanceof DalEntityNotFoundException) {
            return NOT_FOUND;
        }
        if (failure instanceof OptimisticLockingFailureException) {
            return CONFLICT;
        }
        return SERVER_ERROR;
    }

    /**
     * Whether this kind identifies a client fault (validation, not-found, conflict) rather
     * than a genuine service error.
     *
     * @return {@code true} for every kind except {@link #SERVER_ERROR}
     */
    public boolean isClientFault() {
        return this != SERVER_ERROR;
    }
}
