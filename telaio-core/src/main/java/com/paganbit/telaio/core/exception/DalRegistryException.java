package com.paganbit.telaio.core.exception;

/**
 * Base exception for all DAL registry resolution problems.
 * Can be extended for more specific use cases such as missing or ambiguous services.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalRegistryException extends RuntimeException {

    public DalRegistryException(String message) {
        super(message);
    }

    public DalRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
