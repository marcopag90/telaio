package com.paganbit.telaio.rest.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DalIdCodecExceptionTest {

    @Test
    void constructorSetsMessageAndCause() {
        var message = "Failed to decode Base64 ID segment";
        var cause = new IllegalArgumentException("Invalid Base64");

        var exception = new DalIdCodecException(message, cause);

        assertThat(exception)
            .hasMessage(message)
            .hasCause(cause)
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void canBeThrownAndCaught() {
        var cause = new RuntimeException("Original failure");

        var exception = new DalIdCodecException("Encoding failed", cause);

        try {
            throw exception;
        } catch (DalIdCodecException ex) {
            assertThat(ex)
                .hasMessage("Encoding failed")
                .hasCause(cause);
        }
    }
}