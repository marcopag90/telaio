package com.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DalFilterFieldNotReadableExceptionTest {

    @Test
    void constructor_shouldNameThePathAsWrittenInTheFilter() {
        DalFilterFieldNotReadableException exception = new DalFilterFieldNotReadableException("cost_price");

        assertEquals("Filter field 'cost_price' is not readable by the current principal", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void isAnInvalidFilterException_soTheWireAnswerStaysTheGenericClientFault() {
        // The specialization exists for in-process consumers (audit classifies it as DENIED); on the wire
        // it must stay indistinguishable from an unknown field: same handler, same VALIDATION kind.
        DalFilterFieldNotReadableException exception = new DalFilterFieldNotReadableException("salary");

        assertInstanceOf(DalInvalidFilterException.class, exception);
        assertEquals(DalFailureKind.VALIDATION, DalFailureKind.of(exception));
    }
}
