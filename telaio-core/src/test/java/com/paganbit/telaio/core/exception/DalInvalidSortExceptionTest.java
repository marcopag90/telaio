package com.paganbit.telaio.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DalInvalidSortExceptionTest {

    @Test
    void constructor_shouldSetMessage() {
        DalInvalidSortException exception = new DalInvalidSortException("cannot order by that");

        assertEquals("cannot order by that", exception.getMessage());
        assertNull(exception.getCause());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void constructor_shouldSetMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("boom");

        DalInvalidSortException exception = new DalInvalidSortException("cannot apply sort", cause);

        assertEquals("cannot apply sort", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void unknownProperty_shouldNameTheSegmentAndTheWholePath() {
        DalInvalidSortException exception = DalInvalidSortException.unknownProperty("depth", "dims.depth");

        assertEquals("Unknown sort property 'depth' in 'dims.depth'", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void notSortable_shouldNameTheWholePath() {
        DalInvalidSortException exception = DalInvalidSortException.notSortable("tags");

        assertEquals("Sort property 'tags' is not sortable", exception.getMessage());
        assertNull(exception.getCause());
    }
}
