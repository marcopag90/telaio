package com.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DalNotFoundExceptionTest {

    @Test
    void constructor_shouldSetFormattedMessage() {
        String dalName = "testDal";
        String expectedMessage = "DAL service not found: testDal";

        DalNotFoundException exception = new DalNotFoundException(dalName);

        assertEquals(expectedMessage, exception.getMessage());
        assertNull(exception.getCause());
        assertInstanceOf(DalRegistryException.class, exception);
    }
}
