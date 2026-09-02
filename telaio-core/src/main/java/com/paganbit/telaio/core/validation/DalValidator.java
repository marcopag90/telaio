package com.paganbit.telaio.core.validation;

import com.paganbit.telaio.core.exception.DalEntityValidationException;

/**
 * Validates entities in the Telaio Data Access Layer.
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public interface DalValidator {

    /**
     * Validates the provided target object.
     *
     * @param target the object to validate
     * @param type   the entity type the object is validated as
     * @throws DalEntityValidationException if validation fails, containing the validation errors
     */
    void validate(Object target, Class<?> type) throws DalEntityValidationException;
}
