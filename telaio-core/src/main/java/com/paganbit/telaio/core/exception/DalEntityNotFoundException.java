package com.paganbit.telaio.core.exception;

import org.springframework.util.StringUtils;

/**
 * Exception thrown when an entity is not found in the underlined DAL.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalEntityNotFoundException extends RuntimeException {

    public DalEntityNotFoundException(Class<?> entityClass, Object id) {
        super(generateMessage(entityClass.getSimpleName(), id));
    }

    static String generateMessage(String entity, Object id) {
        return StringUtils.capitalize("%s was not found for id: [%s]".formatted(entity, id));
    }
}
