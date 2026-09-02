package com.paganbit.telaio.core.exception;

/**
 * Exception thrown when a well-formed filter references a field the current principal is not allowed
 * to read.
 *
 * <p>A specialization of {@link DalInvalidFilterException} that is <em>deliberately indistinguishable on
 * the wire</em> from an unknown field (same generic client fault, same body), so a filter cannot be used
 * to learn whether a hidden property exists — while remaining distinguishable in-process: repeated
 * probing of hidden fields is an authorization signal, and audit records it as a denied attempt rather
 * than a plain validation failure.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public class DalFilterFieldNotReadableException extends DalInvalidFilterException {

    /**
     * Creates the exception for a filter field the current principal is not allowed to read.
     *
     * @param path the complete field path as written in the filter (never a literal value)
     */
    public DalFilterFieldNotReadableException(String path) {
        super("Filter field '%s' is not readable by the current principal".formatted(path));
    }
}
