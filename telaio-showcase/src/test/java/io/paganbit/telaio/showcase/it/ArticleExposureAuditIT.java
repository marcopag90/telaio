package io.paganbit.telaio.showcase.it;

import io.paganbit.telaio.audit.event.DalAuditEvent;
import io.paganbit.telaio.audit.event.DalAuditOutcome;
import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.showcase.it.AuditCaptureTestConfig.CapturingDalAuditEventStore;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use case — a read-only resource via per-operation exposure, audit, and an implicit row-level read
 * filter. {@code articles} is declared with {@code operations = {READ, READ_ONE}} (writes are not exposed,
 * so they never reach the DAL), {@code @DalAudit}, and a
 * {@code defaultFilter()} that hides non-{@code PUBLISHED} articles from non-power users. This exercises
 * all three end-to-end.
 */
class ArticleExposureAuditIT extends AbstractShowcaseIT {

    private static final String DAL = "articles";

    @Autowired
    private CapturingDalAuditEventStore auditStore;

    @Test
    void readsAreAllowedForEveryRole() {
        for (String user : new String[]{DEVELOPER, ADMIN, USER}) {
            assertThat(list(user, DAL, "size=100").getStatusCode())
                .as("read allowed for %s", user)
                .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void writesAreNotExposedAndReturn405ForEveryRole() {
        String createBody = body(Map.of(
            "title", "Should never persist",
            "slug", "it-should-never-persist",
            "status", "DRAFT",
            "revisionCount", 0
        ));
        // Writes are not exposed (operations = {READ, READ_ONE}); each URI still answers its read sibling,
        // so a write is rejected with 405 (not exposed) rather than 403 (authorization).
        for (String user : new String[]{DEVELOPER, ADMIN, USER}) {
            assertThat(create(user, DAL, createBody).getStatusCode())
                .as("create not exposed for %s", user).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(patch(user, DAL, 1L, body(Map.of("title", "x"))).getStatusCode())
                .as("update not exposed for %s", user).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(delete(user, DAL, 1L).getStatusCode())
                .as("delete not exposed for %s", user).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        }
    }

    @Test
    void userSeesOnlyPublishedArticles() {
        JsonNode page = tree(list(USER, DAL, "size=100"));
        JsonNode content = page.get("content");
        assertThat(content.isEmpty()).as("there are published articles to see").isFalse();
        for (JsonNode article : content) {
            assertThat(article.get("status").asString())
                .as("USER must only ever see PUBLISHED articles")
                .isEqualTo("PUBLISHED");
        }
    }

    @Test
    void powerUsersSeeNonPublishedArticles() {
        JsonNode page = tree(list(DEVELOPER, DAL, "size=100"));
        boolean hasNonPublished = false;
        for (JsonNode article : page.get("content")) {
            if (!"PUBLISHED".equals(article.get("status").asString())) {
                hasNonPublished = true;
                break;
            }
        }
        assertThat(hasNonPublished)
            .as("DEVELOPER sees DRAFT/ARCHIVED articles the implicit filter hides from USER")
            .isTrue();
    }

    @Test
    void implicitFilterHidesADraftFromUserButNotFromPowerUser() {
        String draftId = findArticleIdWithStatus();
        assertThat(draftId).as("seed data contains a DRAFT article").isNotNull();

        assertThat(getOne(USER, DAL, draftId).getStatusCode())
            .as("USER cannot fetch a DRAFT article by id (filtered out -> 404)")
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getOne(DEVELOPER, DAL, draftId).getStatusCode())
            .as("DEVELOPER can fetch the same DRAFT article")
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    void readOperationsAreAudited() {
        auditStore.clear();

        ResponseEntity<String> listed = list(USER, DAL, "size=5");
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(auditStore.events())
            .as("a SUCCESS READ audit event is recorded for the 'articles' DAL with the caller's principal")
            .anySatisfy(this::assertArticleRead);
    }

    private void assertArticleRead(DalAuditEvent event) {
        assertThat(event.dalName()).isEqualTo(DAL);
        assertThat(event.operation()).isEqualTo(DalOperationType.READ);
        assertThat(event.outcome()).isEqualTo(DalAuditOutcome.SUCCESS);
        assertThat(event.principal()).isEqualTo(USER);
    }

    private @Nullable String findArticleIdWithStatus() {
        for (JsonNode article : tree(list(AbstractShowcaseIT.DEVELOPER, DAL, "size=100")).get("content")) {
            if ("DRAFT".equals(article.get("status").asString())) {
                return article.get("id").asString();
            }
        }
        return null;
    }
}
