package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.audit.event.DalAuditEvent;
import com.paganbit.telaio.audit.event.DalAuditOutcome;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.showcase.it.AuditCaptureTestConfig.CapturingDalAuditEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use case — the MongoDB-backed DAL. {@code notifications} extends {@code MongoDal} and runs every
 * operation through the real {@code MongoTransactionManager} declared in {@code MongoConfiguration}
 * (the Testcontainers Mongo is a single-node replica set for that reason), while the other DALs of the
 * same application run on PostgreSQL. This verifies the contract is identical on the second backend:
 * the full create → read → list → patch → delete lifecycle over {@code /dal/v1}, a {@code String}
 * document id in the URL, optimistic-locking {@code version} bumps, {@code q=} filtering converted to
 * a Mongo query, Bean Validation mapped to 400, 404 on a missing id, authentication still required,
 * and the channel-agnostic audit pipeline recording the operations.
 */
class NotificationCrudIT extends AbstractShowcaseIT {

    private static final String DAL = "notifications";

    /**
     * A well-formed ObjectId hex string that no document carries.
     */
    private static final String UNKNOWN_ID = "000000000000000000000000";

    @Autowired
    private CapturingDalAuditEventStore auditStore;

    private Map<String, Object> validNotification() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipient", "it@example.com");
        payload.put("subject", "IT — Mongo backend");
        payload.put("message", "Stored in MongoDB, served by the same /dal/v1 surface.");
        payload.put("channel", "EMAIL");
        return payload;
    }

    @Test
    void fullCrudLifecycle() {
        // CREATE -> 201 with the persisted document (String id assigned, version initialized)
        ResponseEntity<String> created = create(USER, DAL, body(validNotification()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdBody = tree(created);
        String id = createdBody.get("id").asString();
        assertThat(id).isNotBlank();
        assertThat(createdBody.get("subject").asString()).isEqualTo("IT — Mongo backend");
        assertThat(createdBody.get("channel").asString()).isEqualTo("EMAIL");
        long initialVersion = createdBody.get("version").asLong();

        // READ ONE -> 200, addressed by the raw String id
        ResponseEntity<String> fetched = getOne(USER, DAL, id);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(fetched).get("recipient").asString()).isEqualTo("it@example.com");

        // LIST -> 200 with a Spring Page envelope containing the new document
        ResponseEntity<String> listed = list(USER, DAL, "size=100");
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = tree(listed);
        assertThat(page.get("content").isArray()).isTrue();
        // telaio-web returns a PagedModel: pagination metadata is nested under "page".
        assertThat(page.get("page").get("totalElements").asLong()).isPositive();
        assertThat(containsId(page, id)).as("created notification appears in the list").isTrue();

        // PATCH -> 200, only the supplied field changes and the optimistic-locking version is bumped
        ResponseEntity<String> patched = patch(USER, DAL, id, body(Map.of("channel", "SMS")));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode patchedBody = tree(patched);
        assertThat(patchedBody.get("channel").asString()).isEqualTo("SMS");
        assertThat(patchedBody.get("subject").asString()).isEqualTo("IT — Mongo backend");
        assertThat(patchedBody.get("version").asLong()).isGreaterThan(initialVersion);

        // DELETE -> 204, then the document is gone (404)
        ResponseEntity<String> deleted = delete(USER, DAL, id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getOne(USER, DAL, id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void filterIsConvertedToAMongoQuery() {
        Map<String, Object> webhook = validNotification();
        webhook.put("channel", "WEBHOOK");
        webhook.put("recipient", "https://hooks.example.com/it");
        String id = tree(create(USER, DAL, body(webhook))).get("id").asString();

        ResponseEntity<String> matching = list(USER, DAL, "q=channel:'WEBHOOK'&size=100");
        assertThat(matching.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode matchingPage = tree(matching);
        assertThat(containsId(matchingPage, id)).as("the WEBHOOK notification matches its filter").isTrue();
        for (JsonNode node : matchingPage.get("content")) {
            assertThat(node.get("channel").asString()).isEqualTo("WEBHOOK");
        }

        ResponseEntity<String> nonMatching = list(USER, DAL, "q=channel:'SMS'&size=100");
        assertThat(nonMatching.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(containsId(tree(nonMatching), id)).as("filtered out by channel").isFalse();

        assertThat(delete(USER, DAL, id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void temporalFilterComparesAsBsonDate() {
        Map<String, Object> old = validNotification();
        old.put("createdAt", "2020-06-01T00:00:00Z");
        String oldId = tree(create(USER, DAL, body(old))).get("id").asString();
        Map<String, Object> recent = validNotification();
        recent.put("createdAt", "2026-06-01T00:00:00Z");
        String recentId = tree(create(USER, DAL, body(recent))).get("id").asString();

        JsonNode later = tree(list(USER, DAL, "q=createdAt>'2023-01-01T00:00:00Z'&size=100"));
        assertThat(containsId(later, recentId)).as("later document matches >").isTrue();
        assertThat(containsId(later, oldId)).as("earlier document filtered out by >").isFalse();

        JsonNode earlier = tree(list(USER, DAL, "q=createdAt<'2023-01-01T00:00:00Z'&size=100"));
        assertThat(containsId(earlier, oldId)).as("earlier document matches <").isTrue();
        assertThat(containsId(earlier, recentId)).as("later document filtered out by <").isFalse();

        JsonNode exact = tree(list(USER, DAL, "q=createdAt:'2020-06-01T00:00:00Z'&size=100"));
        assertThat(containsId(exact, oldId)).as("exact instant matches").isTrue();
        assertThat(containsId(exact, recentId)).as("other instant filtered out by :").isFalse();

        assertThat(delete(USER, DAL, oldId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete(USER, DAL, recentId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void blankRecipientIsRejectedWith400AndFieldError() {
        Map<String, Object> payload = validNotification();
        payload.put("recipient", "");
        ResponseEntity<String> response = create(USER, DAL, body(payload));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode errors = tree(response).get("errors");
        assertThat(errors.isArray()).isTrue();
        boolean recipientError = false;
        for (JsonNode error : errors) {
            if ("recipient".equals(error.get("field").asString())) {
                recipientError = true;
                break;
            }
        }
        assertThat(recipientError).as("validation error reported for 'recipient'").isTrue();
    }

    @Test
    void missingRequiredChannelIsRejectedWith400() {
        Map<String, Object> payload = validNotification();
        payload.remove("channel");
        assertThat(create(USER, DAL, body(payload)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getByUnknownIdReturns404() {
        assertThat(getOne(USER, DAL, UNKNOWN_ID).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() {
        assertThat(list(null, DAL, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void operationsAreAuditedOnTheMongoBackend() {
        auditStore.clear();

        ResponseEntity<String> created = create(USER, DAL, body(validNotification()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = tree(created).get("id").asString();

        assertThat(auditStore.events())
            .as("a SUCCESS CREATE audit event is recorded for the 'notifications' DAL with the caller's principal")
            .anySatisfy(event -> assertNotificationEvent(event, DalOperationType.CREATE));

        assertThat(delete(USER, DAL, id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(auditStore.events())
            .as("the transactional delete is audited as well")
            .anySatisfy(event -> assertNotificationEvent(event, DalOperationType.DELETE));
    }

    private void assertNotificationEvent(DalAuditEvent event, DalOperationType operation) {
        assertThat(event.dalName()).isEqualTo(DAL);
        assertThat(event.operation()).isEqualTo(operation);
        assertThat(event.outcome()).isEqualTo(DalAuditOutcome.SUCCESS);
        assertThat(event.principal()).isEqualTo(USER);
    }

    private static boolean containsId(JsonNode page, String id) {
        for (JsonNode node : page.get("content")) {
            if (id.equals(node.get("id").asString())) {
                return true;
            }
        }
        return false;
    }
}
