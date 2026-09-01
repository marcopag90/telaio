package com.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DalSortFieldNotReadableExceptionTest {

    @Test
    void constructor_shouldNameThePathAsWrittenInTheSort() {
        DalSortFieldNotReadableException exception = new DalSortFieldNotReadableException("cost_price");

        assertEquals("Sort property 'cost_price' is not readable by the current principal", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void isAnInvalidSortException_soTheWireAnswerStaysTheGenericClientFault() {
        // The specialization exists for in-process consumers (audit classifies it as DENIED); on the wire
        // it must stay indistinguishable from an unknown sort property: same handler, same VALIDATION kind.
        DalSortFieldNotReadableException exception = new DalSortFieldNotReadableException("salary");

        assertInstanceOf(DalInvalidSortException.class, exception);
        assertEquals(DalFailureKind.VALIDATION, DalFailureKind.of(exception));
    }
}
