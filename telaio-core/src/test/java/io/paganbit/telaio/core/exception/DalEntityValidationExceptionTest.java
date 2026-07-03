package io.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.validation.FieldError;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DalEntityValidationExceptionTest {

    @Test
    void constructor_shouldStoreErrors() {
        // Given
        List<FieldError> errors = new ArrayList<>();
        errors.add(new FieldError("object1", "test1", "Test error message 1"));
        errors.add(new FieldError("object2", "test2", "Test error message 2"));

        // When
        DalEntityValidationException exception = new DalEntityValidationException(errors);

        // Then
        assertSame(errors, exception.getErrors());
    }

    @Test
    void getErrors_shouldReturnStoredErrors() {
        // Given
        List<FieldError> errors = new ArrayList<>();
        errors.add(new FieldError("object1", "test1", "Test error message 1"));
        errors.add(new FieldError("object2", "test2", "Test error message 2"));
        DalEntityValidationException exception = new DalEntityValidationException(errors);

        // When
        List<FieldError> returnedErrors = exception.getErrors();

        // Then
        assertEquals(2, returnedErrors.size());
        assertEquals("Test error message 1", returnedErrors.get(0).getDefaultMessage());
        assertEquals("Test error message 2", returnedErrors.get(1).getDefaultMessage());
    }
}