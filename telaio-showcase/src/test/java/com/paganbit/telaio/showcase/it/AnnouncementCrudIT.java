package com.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use case — the simplest possible DAL. {@code announcements} has no {@code @DalSecurity}, no audit,
 * and metrics disabled, so this verifies the bare CRUD contract any {@code @DalService} gets for free:
 * the full create → read → list → patch → delete lifecycle, Bean Validation mapped to 400, 404 on a
 * missing id, and that authentication is still required by the application.
 */
class AnnouncementCrudIT extends AbstractShowcaseIT {

    private static final String DAL = "announcements";

    private Map<String, Object> validAnnouncement() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "IT — New feature shipped");
        payload.put("message", "The end-to-end test suite is now the release gate.");
        payload.put("type", "INFO");
        return payload;
    }

    @Test
    void fullCrudLifecycle() {
        // CREATE -> 201 with the persisted entity (id assigned, fields echoed)
        ResponseEntity<String> created = create(USER, DAL, body(validAnnouncement()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdBody = tree(created);
        assertThat(createdBody.get("id")).isNotNull();
        assertThat(createdBody.get("title").asString()).isEqualTo("IT — New feature shipped");
        assertThat(createdBody.get("type").asString()).isEqualTo("INFO");
        String id = createdBody.get("id").asString();

        // READ ONE -> 200
        ResponseEntity<String> fetched = getOne(USER, DAL, id);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(fetched).get("message").asString())
            .isEqualTo("The end-to-end test suite is now the release gate.");

        // LIST -> 200 with a Spring Page envelope containing the new row
        ResponseEntity<String> listed = list(USER, DAL, "size=100");
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = tree(listed);
        assertThat(page.get("content").isArray()).isTrue();
        // telaio-web returns a PagedModel: pagination metadata is nested under "page".
        assertThat(page.get("page").get("totalElements").asLong()).isPositive();
        boolean present = false;
        for (JsonNode node : page.get("content")) {
            if (id.equals(node.get("id").asString())) {
                present = true;
                break;
            }
        }
        assertThat(present).as("created announcement appears in the list").isTrue();

        // PATCH -> 200, only the supplied field changes, the rest is preserved
        ResponseEntity<String> patched = patch(USER, DAL, id, body(Map.of("type", "CRITICAL")));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode patchedBody = tree(patched);
        assertThat(patchedBody.get("type").asString()).isEqualTo("CRITICAL");
        assertThat(patchedBody.get("title").asString()).isEqualTo("IT — New feature shipped");

        // DELETE -> 204, then the row is gone (404)
        ResponseEntity<String> deleted = delete(USER, DAL, id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getOne(USER, DAL, id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void blankTitleIsRejectedWith400AndFieldError() {
        Map<String, Object> payload = validAnnouncement();
        payload.put("title", "");

        ResponseEntity<String> response = create(USER, DAL, body(payload));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode errors = tree(response).get("errors");
        assertThat(errors.isArray()).isTrue();
        boolean titleError = false;
        for (JsonNode error : errors) {
            if ("title".equals(error.get("field").asString())) {
                titleError = true;
                break;
            }
        }
        assertThat(titleError).as("validation error reported for 'title'").isTrue();
    }

    @Test
    void missingRequiredTypeIsRejectedWith400() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "No type");
        payload.put("message", "Missing the required type.");

        assertThat(create(USER, DAL, body(payload)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getByUnknownIdReturns404() {
        assertThat(getOne(USER, DAL, 999_999_999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() {
        assertThat(list(null, DAL, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
