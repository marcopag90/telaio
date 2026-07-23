package com.paganbit.telaio.rest.client.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the message/cause wiring of the exception leaves that are not exercised by the mapper
 * tests: the caller-side (no-response) failures and the two-argument {@code (message, cause)}
 * constructor of the malformed-response exception.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DalClientExceptionsTest {

    @Test
    void transportExceptionCarriesMessageAndCause() {
        Throwable cause = new ConnectException("connection refused");

        DalClientTransportException ex = new DalClientTransportException("I/O failure", cause);

        assertThat(ex.getMessage()).isEqualTo("I/O failure");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex).isInstanceOf(DalClientException.class);
    }

    @Test
    void encodingExceptionCarriesMessageAndCause() {
        Throwable cause = new IllegalArgumentException("bad id");

        DalClientEncodingException ex = new DalClientEncodingException("cannot encode id", cause);

        assertThat(ex.getMessage()).isEqualTo("cannot encode id");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex).isInstanceOf(DalClientException.class);
    }

    @Test
    void malformedResponseExceptionSupportsMessageOnly() {
        DalClientMalformedResponseException ex =
            new DalClientMalformedResponseException("not a page");

        assertThat(ex.getMessage()).isEqualTo("not a page");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void malformedResponseExceptionSupportsMessageAndCause() {
        Throwable cause = new RuntimeException("parse error");

        DalClientMalformedResponseException ex =
            new DalClientMalformedResponseException("not a page", cause);

        assertThat(ex.getMessage()).isEqualTo("not a page");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
