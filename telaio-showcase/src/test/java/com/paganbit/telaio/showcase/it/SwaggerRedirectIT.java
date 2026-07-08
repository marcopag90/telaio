package com.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting — the application root is a public convenience entry point: an anonymous {@code GET /}
 * must redirect to the Swagger UI (not answer {@code 401}), and the redirect target itself must be
 * anonymously reachable, so the two assertions together guarantee the redirect is coherent end-to-end.
 */
class SwaggerRedirectIT extends AbstractShowcaseIT {

    @Test
    void rootRedirectsAnonymouslyToSwaggerUi() throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                .GET()
                .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            assertThat(HttpStatus.valueOf(response.statusCode()).is3xxRedirection())
                .as("the public root must redirect, not challenge with 401 or serve content (got %s)",
                    response.statusCode())
                .isTrue();
            assertThat(response.headers().firstValue("Location"))
                .hasValueSatisfying(location ->
                    assertThat(URI.create(location).getPath()).isEqualTo("/swagger-ui/index.html"));
        }
    }

    @Test
    void swaggerUiIsReachableAnonymously() {
        ResponseEntity<String> response =
            exchange(null, HttpMethod.GET, "http://localhost:" + port + "/swagger-ui/index.html", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
