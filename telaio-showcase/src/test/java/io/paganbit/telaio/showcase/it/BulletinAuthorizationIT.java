package io.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identity-aware CRUD authorization. {@code bulletins} is wired with {@link
 * io.paganbit.telaio.showcase.dal.bulletin.AdminWritesDalAuthAdapter}: everyone may read, only
 * {@code ADMIN} may write. Unlike per-operation exposure, the write endpoints exist and are documented —
 * a non-admin write is denied with {@code 403} (authorization), not {@code 405} (not exposed).
 */
class BulletinAuthorizationIT extends AbstractShowcaseIT {

    private static final String DAL = "bulletins";

    @Test
    void readsAreAllowedForEveryRole() {
        for (String user : new String[]{DEVELOPER, ADMIN, USER}) {
            assertThat(list(user, DAL, "size=100").getStatusCode())
                .as("read allowed for %s", user)
                .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void adminCanCreate() {
        String createBody = body(Map.of(
            "title", "Maintenance tonight",
            "message", "Expect a short outage around 02:00 UTC."
        ));
        assertThat(create(ADMIN, DAL, createBody).getStatusCode())
            .as("ADMIN may create a bulletin")
            .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void writesAreDeniedForNonAdminsWith403() {
        String createBody = body(Map.of(
            "title", "Should never persist",
            "message", "Only ADMIN may post bulletins."
        ));
        for (String user : new String[]{DEVELOPER, USER}) {
            assertThat(create(user, DAL, createBody).getStatusCode())
                .as("create denied for %s", user).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(patch(user, DAL, 1L, body(Map.of("title", "x"))).getStatusCode())
                .as("update denied for %s", user).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(delete(user, DAL, 1L).getStatusCode())
                .as("delete denied for %s", user).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
