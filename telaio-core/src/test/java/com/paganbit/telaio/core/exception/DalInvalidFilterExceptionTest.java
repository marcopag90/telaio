package com.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DalInvalidFilterExceptionTest {

    @Test
    void constructor_shouldSetMessage() {
        DalInvalidFilterException exception = new DalInvalidFilterException("unsupported function");

        assertEquals("unsupported function", exception.getMessage());
        assertNull(exception.getCause());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void constructor_shouldSetMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("boom");

        DalInvalidFilterException exception = new DalInvalidFilterException("cannot apply filter", cause);

        assertEquals("cannot apply filter", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void unknownField_shouldNameTheSegmentAndTheWholePath() {
        DalInvalidFilterException exception = DalInvalidFilterException.unknownField("depth", "dims.depth");

        assertEquals("Unknown filter field 'depth' in 'dims.depth'", exception.getMessage());
        assertNull(exception.getCause());
    }
}
