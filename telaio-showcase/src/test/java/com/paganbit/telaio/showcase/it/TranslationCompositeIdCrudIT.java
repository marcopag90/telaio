package com.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use case — a DAL with a <em>composite</em> id ({@code translations}, keyed by message key + locale).
 * Exercises the full {@code @DalId} Base64 round-trip end to end: on CREATE the id is a nested JSON object
 * in the body, while READ/PATCH/DELETE address the row by a Base64 URL-safe encoded JSON id in the path
 * (decoded by {@code DalIdArgumentResolver} and resolved by the JPA composite-key specification).
 */
class TranslationCompositeIdCrudIT extends AbstractShowcaseIT {

    private static final String DAL = "translations";

    @Test
    void fullCompositeIdLifecycle() {
        Map<String, Object> id = compositeId("it.test.key", "en");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("value", "Hello");

        // CREATE — the composite id is supplied as a nested object in the body.
        ResponseEntity<String> created = create(USER, DAL, body(payload));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdBody = tree(created);
        assertThat(createdBody.get("id").get("messageKey").asString()).isEqualTo("it.test.key");
        assertThat(createdBody.get("id").get("locale").asString()).isEqualTo("en");
        assertThat(createdBody.get("value").asString()).isEqualTo("Hello");

        String encodedId = encodeId(id);

        // READ ONE — addressed by the Base64-encoded composite id.
        ResponseEntity<String> fetched = getOne(USER, DAL, encodedId);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(fetched).get("value").asString()).isEqualTo("Hello");

        // PATCH — same encoded id; only the supplied field changes.
        ResponseEntity<String> patched = patch(USER, DAL, encodedId, body(Map.of("value", "Hi")));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(patched).get("value").asString()).isEqualTo("Hi");

        // DELETE — same encoded id; then the row is gone.
        ResponseEntity<String> deleted = delete(USER, DAL, encodedId);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getOne(USER, DAL, encodedId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownCompositeIdReturns404() {
        String encodedId = encodeId(compositeId("does.not.exist", "zz"));

        assertThat(getOne(USER, DAL, encodedId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static Map<String, Object> compositeId(String messageKey, String locale) {
        Map<String, Object> id = new LinkedHashMap<>();
        id.put("messageKey", messageKey);
        id.put("locale", locale);
        return id;
    }

    private static String encodeId(Map<String, Object> id) {
        String json = JSON.writeValueAsString(id);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
