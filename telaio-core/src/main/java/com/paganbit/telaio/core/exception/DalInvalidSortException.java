package com.paganbit.telaio.core.exception;

/**
 * Exception thrown when a sort parameter cannot be applied to the target entity: it references a
 * property the entity does not expose or does not persist, or that the current principal is not
 * allowed to read, or one the persistence backend cannot order by.
 *
 * <p>This is a <em>client fault</em> ({@link DalFailureKind#VALIDATION}): the request cannot be satisfied
 * as-is and must be corrected by the caller. The message may name the offending property, never a
 * literal value. The read-permission case is raised as the {@link DalSortFieldNotReadableException}
 * specialisation — same wire answer, distinguishable for audit.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public class DalInvalidSortException extends RuntimeException {

    /**
     * Creates the exception with a message describing the offending part of the sort.
     *
     * @param message the reason the sort was rejected
     */
    public DalInvalidSortException(String message) {
        super(message);
    }

    /**
     * Creates the exception wrapping the failure raised while applying the sort.
     *
     * @param message the reason the sort was rejected
     * @param cause   the underlying failure
     */
    public DalInvalidSortException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception for a sort property that cannot be resolved on the target entity.
     *
     * @param segment the path segment that could not be resolved
     * @param path    the complete property path as written in the sort
     * @return the exception to throw
     */
    public static DalInvalidSortException unknownProperty(String segment, String path) {
        return new DalInvalidSortException(
            "Unknown sort property '%s' in '%s'".formatted(segment, path));
    }

    /**
     * Creates the exception for a resolvable property the persistence backend cannot order by
     * (e.g. a collection or map-valued attribute as the terminal path segment).
     *
     * @param path the complete property path as written in the sort
     * @return the exception to throw
     */
    public static DalInvalidSortException notSortable(String path) {
        return new DalInvalidSortException(
            "Sort property '%s' is not sortable".formatted(path));
    }
}
