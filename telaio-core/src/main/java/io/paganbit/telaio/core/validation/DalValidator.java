package io.paganbit.telaio.core.validation;

import io.paganbit.telaio.core.exception.DalEntityValidationException;

/**
 * Interface for validating objects in the Telaio Data Access Layer.
 * Implementations of this interface provide validation logic for specific types.
 *
 * @param <T> the type of object to validate
 */
public interface DalValidator<T> {

    /**
     * Validates the provided target object.
     *
     * @param target the object to validate
     * @throws DalEntityValidationException if validation fails, containing the validation errors
     */
    void validate(T target) throws DalEntityValidationException;
}
