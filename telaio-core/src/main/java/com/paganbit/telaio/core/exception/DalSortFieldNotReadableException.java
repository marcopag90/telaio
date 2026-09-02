package com.paganbit.telaio.core.exception;

/**
 * Exception thrown when a sort references a property the current principal is not allowed to read.
 *
 * <p>A specialization of {@link DalInvalidSortException} that is <em>deliberately indistinguishable on
 * the wire</em> from an unknown sort property (same generic client fault, same body), so a sort cannot
 * be used to learn whether a hidden property exists — while remaining distinguishable in-process:
 * repeated probing of hidden fields is an authorization signal, and audit records it as a denied
 * attempt rather than a plain validation failure.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public class DalSortFieldNotReadableException extends DalInvalidSortException {

    /**
     * Creates the exception for a sort property the current principal is not allowed to read.
     *
     * @param path the complete property path as written in the sort (never a literal value)
     */
    public DalSortFieldNotReadableException(String path) {
        super("Sort property '%s' is not readable by the current principal".formatted(path));
    }
}
