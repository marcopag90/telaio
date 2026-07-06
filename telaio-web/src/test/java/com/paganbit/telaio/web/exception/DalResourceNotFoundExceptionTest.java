package com.paganbit.telaio.web.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DalResourceNotFoundExceptionTest {

    @Test
    void testExceptionMessage() {
        Object id = 123;
        DalResourceNotFoundException exception = new DalResourceNotFoundException(id);
        assertEquals("Resource not found with ID: 123", exception.getMessage());
    }

    @Test
    void testExceptionWithNullId() {
        DalResourceNotFoundException exception = new DalResourceNotFoundException(1);
        assertEquals("Resource not found with ID: 1", exception.getMessage());
    }
}
