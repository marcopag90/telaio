package io.paganbit.telaio.core.exception;

/**
 * Exception thrown when a DAL service is not found in the registry.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalNotFoundException extends DalRegistryException {

    public DalNotFoundException(String name) {
        super("DAL service not found: %s".formatted(name));
    }
}
