package com.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DalEntityNotFoundExceptionTest {

    private static class DummyEntity {
    }

    @Test
    void constructor_shouldGenerateCorrectMessage() {
        Long id = 42L;

        final var ex = new DalEntityNotFoundException(DummyEntity.class, id);

        assertEquals("DummyEntity was not found for id: [42]", ex.getMessage());
    }
}
