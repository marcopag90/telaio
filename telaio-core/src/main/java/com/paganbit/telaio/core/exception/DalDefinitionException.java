package com.paganbit.telaio.core.exception;

/**
 * Exception thrown when a DAL component is incorrectly defined.
 * <p>
 * This helps identify developer misconfigurations early in the application startup phase.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalDefinitionException extends RuntimeException {

    public DalDefinitionException(String message) {
        super(message);
    }

    public DalDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
