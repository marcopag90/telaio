package io.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DalRegistryExceptionTest {

    @Test
    void constructorWithMessage_shouldSetMessage() {
        String expectedMessage = "Registry error";

        DalRegistryException exception = new DalRegistryException(expectedMessage);

        assertEquals(expectedMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void constructorWithMessageAndCause_shouldSetMessageAndCause() {
        String expectedMessage = "Registry error";
        Throwable expectedCause = new RuntimeException("Original cause");

        DalRegistryException exception = new DalRegistryException(expectedMessage, expectedCause);

        assertEquals(expectedMessage, exception.getMessage());
        assertEquals(expectedCause, exception.getCause());
    }
}
