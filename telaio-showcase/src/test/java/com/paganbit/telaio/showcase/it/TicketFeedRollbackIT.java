package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.rest.client.DalPage;
import com.paganbit.telaio.rest.client.DalPageRequest;
import com.paganbit.telaio.rest.client.blocking.TelaioClient;
import com.paganbit.telaio.rest.client.blocking.TelaioRestClient;
import com.paganbit.telaio.rest.client.blocking.v1.DalClient;
import com.paganbit.telaio.rest.client.exception.DalClientServerException;
import com.paganbit.telaio.showcase.TestcontainersConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the transactional contract of {@code SupportTicketDalService}: the feed publication runs
 * inside the write's transaction, so when the remote {@code feed} call fails the ticket write is
 * rolled back and nothing is persisted.
 *
 * <p>The failure is forced by pointing the {@code self} connection at a dead port (nothing listens),
 * while the app itself is reachable on a separate, live port used by the test client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TicketFeedRollbackIT {

    private static final int[] PORTS = findTwoFreePorts();
    private static final int SERVER_PORT = PORTS[0];
    private static final int DEAD_FEED_PORT = PORTS[1];
    private static final String USER = "user";

    @DynamicPropertySource
    static void wireBrokenSelfConnection(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> SERVER_PORT);
        // Unreachable target: the in-app feed call will be refused, failing the write.
        registry.add("telaio.rest-client.connections.self.base-url",
            () -> "http://localhost:" + DEAD_FEED_PORT);
    }

    record Ticket(@Nullable Long id, @Nullable String subject, @Nullable String status) {
    }

    private DalClient<Ticket, Long> tickets;

    @BeforeEach
    void setUpClient() {
        TelaioClient client = TelaioRestClient.create(RestClient.builder()
            .baseUrl("http://localhost:" + SERVER_PORT)
            .requestInterceptor(new BasicAuthenticationInterceptor(USER, USER))
            .build());
        tickets = client.dal("tickets", Ticket.class, Long.class);
    }

    @Test
    void aFailedFeedCallRollsBackTheTicketWrite() {
        String subject = "rollback-" + UUID.randomUUID();

        // The in-app feed publication cannot reach the dead port; the unchecked DalClientException
        // propagates and the server answers 5xx.
        Ticket newTicket = new Ticket(null, subject, "OPEN");
        assertThatThrownBy(() -> tickets.create(newTicket))
            .isInstanceOf(DalClientServerException.class);

        // The transaction rolled back: no ticket with that subject was persisted.
        DalPage<Ticket> page = tickets.read("subject ~ '*" + subject + "*'", DalPageRequest.of(0, 10));
        assertThat(page.content()).isEmpty();
    }

    /**
     * Allocates two distinct free ports. Both sockets are held open at the same time so the OS
     * cannot hand back the same number twice — otherwise the "dead" feed port could collide with
     * the live server port and the rollback would never be exercised.
     */
    private static int[] findTwoFreePorts() {
        try (ServerSocket first = new ServerSocket(0);
             ServerSocket second = new ServerSocket(0)) {
            return new int[]{first.getLocalPort(), second.getLocalPort()};
        } catch (IOException e) {
            throw new UncheckedIOException("Could not allocate free ports for the test", e);
        }
    }
}
