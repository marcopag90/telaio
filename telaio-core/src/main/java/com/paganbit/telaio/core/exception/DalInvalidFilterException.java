package com.paganbit.telaio.core.exception;

/**
 * Exception thrown when a syntactically valid filter cannot be applied to the target entity: it
 * references a field the entity does not expose or does not persist, or that the current principal is not
 * allowed to read, or uses a function that is unknown or unsupported by the persistence backend.
 *
 * <p>This is a <em>client fault</em> ({@link DalFailureKind#VALIDATION}): the request cannot be satisfied
 * as-is and must be corrected by the caller. The message may name the offending field, never a literal
 * value. A literal that does not convert to the field's type is not covered: it fails inside the
 * persistence layer, on every backend alike. The read-permission case is raised as the
 * {@link DalFilterFieldNotReadableException} specialisation — same wire answer, distinguishable for
 * audit.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public class DalInvalidFilterException extends RuntimeException {

    /**
     * Creates the exception with a message describing the offending part of the filter.
     *
     * @param message the reason the filter was rejected
     */
    public DalInvalidFilterException(String message) {
        super(message);
    }

    /**
     * Creates the exception wrapping the failure raised while applying the filter.
     *
     * @param message the reason the filter was rejected
     * @param cause   the underlying failure (e.g. a literal conversion error)
     */
    public DalInvalidFilterException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception for a filter field that cannot be resolved on the target entity.
     *
     * @param segment the path segment that could not be resolved
     * @param path    the complete field path as written in the filter
     * @return the exception to throw
     */
    public static DalInvalidFilterException unknownField(String segment, String path) {
        return new DalInvalidFilterException(
            "Unknown filter field '%s' in '%s'".formatted(segment, path));
    }
}
