package com.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting — generic REST API error handling that is independent of any single DAL: authentication
 * is required, an unknown DAL name is a 404, and a malformed filter is rejected rather than silently
 * ignored (which would otherwise leak every row).
 */
class DalApiErrorsIT extends AbstractShowcaseIT {

    @Test
    void unauthenticatedAccessIsRejectedWith401() {
        assertThat(list(null, "products", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownDalNameReturns404() {
        assertThat(list(USER, "no-such-dal", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void malformedFilterIsRejectedNotSilentlyIgnored() {
        // Passed raw (TestRestTemplate encodes once); unbalanced parens are a Turkraft syntax error.
        ResponseEntity<String> response = list(USER, "products", "q=(((");

        assertThat(response.getStatusCode().is2xxSuccessful())
            .as("a malformed filter must never be treated as 'no filter' and return rows")
            .isFalse();
        assertThat(response.getStatusCode().isError()).isTrue();
    }
}
