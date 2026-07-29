package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.rest.client.DalPage;
import com.paganbit.telaio.rest.client.DalPageRequest;
import com.paganbit.telaio.rest.client.blocking.TelaioClient;
import com.paganbit.telaio.rest.client.blocking.TelaioRestClient;
import com.paganbit.telaio.rest.client.blocking.v1.DalClient;
import com.paganbit.telaio.showcase.TestcontainersConfiguration;
import com.paganbit.telaio.showcase.dal.ticket.FeedActivity;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end proof of the DAL-to-DAL round-trip powered by the compile-scope
 * {@code telaio-rest-client}: writing a {@code tickets} row makes {@code SupportTicketDalService}
 * call this same application's {@code POST /dal/v1/feed} through the configured {@code self}
 * connection (basic auth via {@code ShowcaseRestClientConfig}), and an activity entry appears.
 *
 * <p>Unlike {@link TelaioClientRoundTripIT} this test runs on a <b>fixed, pre-allocated port</b>:
 * the app's own {@code TelaioClient} binds its base URL at startup, so the self-call target must be
 * known before the context refreshes — a random port cannot be injected in time. The same port
 * backs both {@code server.port} and {@code telaio.rest-client.connections.self.base-url}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TicketFeedRoundTripIT {

    private static final int PORT = findFreePort();
    private static final String USER = "user";

    @DynamicPropertySource
    static void wireSelfConnection(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("telaio.rest-client.connections.self.base-url", () -> "http://localhost:" + PORT);
    }

    record Ticket(@Nullable Long id, @Nullable String subject, @Nullable String status) {
    }

    private DalClient<Ticket, Long> tickets;
    private DalClient<FeedActivity, Long> feed;

    @BeforeEach
    void setUpClient() {
        TelaioClient client = TelaioRestClient.create(RestClient.builder()
            .baseUrl("http://localhost:" + PORT)
            .requestInterceptor(new BasicAuthenticationInterceptor(USER, USER))
            .build());
        tickets = client.dal("tickets", Ticket.class, Long.class);
        feed = client.dal("feed", FeedActivity.class, Long.class);
    }

    @Test
    void creatingATicketPublishesAnOpenedActivityToTheFeed() {
        String subject = "vpn-access-" + UUID.randomUUID();

        Ticket created = tickets.create(new Ticket(null, subject, "OPEN"));
        assertThat(created.id()).isNotNull();

        DalPage<FeedActivity> activities = feed.read(
            "message ~ '*" + subject + "*'", DalPageRequest.of(0, 10));
        assertThat(activities.content())
            .singleElement()
            .satisfies(activity -> {
                assertThat(activity.source()).isEqualTo("tickets");
                assertThat(activity.message()).isEqualTo("Ticket opened: " + subject);
            });
    }

    @Test
    void updatingATicketPublishesAnUpdatedActivityToTheFeed() {
        String subject = "laptop-swap-" + UUID.randomUUID();
        Ticket created = tickets.create(new Ticket(null, subject, "OPEN"));
        assertNotNull(created.id());
        long id = created.id();

        Optional<Ticket> updated = tickets.update(id, Map.of("status", "CLOSED"));
        assertThat(updated).hasValueSatisfying(t -> assertThat(t.status()).isEqualTo("CLOSED"));

        DalPage<FeedActivity> activities = feed.read(
            "message : 'Ticket " + id + " updated'", DalPageRequest.of(0, 10));
        assertThat(activities.content())
            .singleElement()
            .satisfies(activity -> assertThat(activity.source()).isEqualTo("tickets"));
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not allocate a free port for the test server", e);
        }
    }
}
