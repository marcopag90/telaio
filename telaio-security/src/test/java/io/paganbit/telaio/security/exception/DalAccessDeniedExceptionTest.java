package io.paganbit.telaio.security.exception;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class DalAccessDeniedExceptionTest {

    @Test
    void constructorWithMessage_shouldSetMessage() {
        String expectedMessage = "Access denied for DAL operation";

        DalAccessDeniedException exception = new DalAccessDeniedException(expectedMessage);

        assertEquals(expectedMessage, exception.getMessage());
        assertNull(exception.getCause());
        assertInstanceOf(AccessDeniedException.class, exception, "Should be an instance of AccessDeniedException");
    }

    @Test
    void constructorWithMessageAndCause_shouldSetMessageAndCause() {
        String expectedMessage = "Access denied for DAL operation";
        Throwable expectedCause = new RuntimeException("Original cause");

        DalAccessDeniedException exception = new DalAccessDeniedException(expectedMessage, expectedCause);

        assertEquals(expectedMessage, exception.getMessage());
        assertEquals(expectedCause, exception.getCause());
        assertInstanceOf(AccessDeniedException.class, exception, "Should be an instance of AccessDeniedException");
    }
}
