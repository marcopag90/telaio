package io.paganbit.telaio.showcase.it;

import io.paganbit.telaio.showcase.TestcontainersConfiguration;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Base class for the showcase end-to-end integration tests. Boots the whole application on a random
 * port against a real PostgreSQL (Testcontainers) and drives it over genuine HTTP through the full
 * Spring Security filter chain — exactly as a client would.
 *
 * <p>All subclasses share the <em>identical</em> annotations below so Spring caches a single
 * application context (and therefore a single database container) across the entire suite. Helpers
 * authenticate with the showcase's three in-memory users, whose username equals their password:
 * {@value #DEVELOPER}, {@value #ADMIN}, {@value #USER}; pass {@code null} for an anonymous request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, AuditCaptureTestConfig.class})
abstract class AbstractShowcaseIT {

    protected static final String DEVELOPER = "developer";
    protected static final String ADMIN = "admin";
    protected static final String USER = "user";

    /**
     * Jackson 3 mapper for building request bodies and reading response trees.
     */
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    // --- URL building -------------------------------------------------------------------------

    protected String url(String dalName) {
        return "http://localhost:" + port + "/dal/v1/" + dalName;
    }

    protected String url(String dalName, Object id) {
        return url(dalName) + "/" + id;
    }

    // --- REST verbs (each returns the raw response so tests assert status and body) -----------

    protected ResponseEntity<String> create(@Nullable String user, String dalName, String jsonBody) {
        return exchange(user, HttpMethod.POST, url(dalName), jsonBody);
    }

    protected ResponseEntity<String> list(@Nullable String user, String dalName, @Nullable String query) {
        String target = query == null ? url(dalName) : url(dalName) + "?" + query;
        return exchange(user, HttpMethod.GET, target, null);
    }

    protected ResponseEntity<String> getOne(@Nullable String user, String dalName, Object id) {
        return exchange(user, HttpMethod.GET, url(dalName, id), null);
    }

    protected ResponseEntity<String> patch(@Nullable String user, String dalName, Object id, String jsonBody) {
        return exchange(user, HttpMethod.PATCH, url(dalName, id), jsonBody);
    }

    protected ResponseEntity<String> delete(@Nullable String user, String dalName, Object id) {
        return exchange(user, HttpMethod.DELETE, url(dalName, id), null);
    }

    protected ResponseEntity<String> exchange(
        @Nullable String user,
        HttpMethod method,
        String url,
        @Nullable String body
    ) {
        HttpHeaders headers = new HttpHeaders();
        if (user != null) {
            // The showcase seeds each user with username == password.
            headers.setBasicAuth(user, user);
        }
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);
        return rest.exchange(url, method, httpEntity, String.class);
    }

    // --- JSON helpers -------------------------------------------------------------------------

    /**
     * Parses a response body into a Jackson tree.
     */
    protected JsonNode tree(ResponseEntity<String> response) {
        return JSON.readTree(response.getBody());
    }

    /**
     * Serialises a map to a JSON request body. Use a raw JSON string when null values are needed.
     */
    protected String body(Map<String, ?> map) {
        return JSON.writeValueAsString(map);
    }
}
