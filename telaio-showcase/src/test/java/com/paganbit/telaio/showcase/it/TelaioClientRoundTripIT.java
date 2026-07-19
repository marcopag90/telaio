package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.rest.client.*;
import com.paganbit.telaio.rest.client.blocking.TelaioRestClient;
import com.paganbit.telaio.rest.client.exception.DalClientNotFoundException;
import com.paganbit.telaio.rest.client.exception.DalClientValidationException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip guard between telaio-rest-client and telaio-web: drives the real showcase application
 * (full security chain, PostgreSQL via Testcontainers) through the typed {@code TelaioRestClient}
 * instead of raw HTTP. This is the wire-contract drift detector — if the client-side encoding
 * (paths, composite-id Base64, paging, problem parsing) ever diverges from the server, it fails
 * here first.
 */
class TelaioClientRoundTripIT extends AbstractShowcaseIT {

    record Announcement(
        @Nullable Long id,
        @Nullable String title,
        @Nullable String message,
        @Nullable String type
    ) {
    }

    record TranslationId(String messageKey, String locale) {
    }

    record Translation(@Nullable TranslationId id, @Nullable String value) {
    }

    private DalClient<Announcement, Long> announcements;
    private DalClient<Translation, TranslationId> translations;

    @BeforeEach
    void setUpClient() {
        TelaioClient client = TelaioRestClient.create(RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .requestInterceptor(new BasicAuthenticationInterceptor(USER, USER))
            .build());

        announcements = client.dal("announcements", Announcement.class, Long.class);
        translations = client.dal("translations", Translation.class, TranslationId.class);
    }

    @Test
    void simpleIdCrudLifecycleThroughTypedClient() {
        String title = "client-it-" + UUID.randomUUID();

        // CREATE via DTO: the null id must be dropped from the wire payload.
        Announcement created = announcements.create(
            new Announcement(null, title, "Created through TelaioRestClient.", "INFO"));
        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo(title);

        // READ ONE
        assertThat(announcements.readOne(created.id()))
            .hasValueSatisfying(fetched ->
                assertThat(fetched.message()).isEqualTo("Created through TelaioRestClient."));

        // UPDATE via Map: only the supplied field changes.
        Optional<Announcement> patched = announcements.update(created.id(), Map.of("type", "CRITICAL"));
        assertThat(patched).hasValueSatisfying(updated -> {
            assertThat(updated.type()).isEqualTo("CRITICAL");
            assertThat(updated.title()).isEqualTo(title);
        });

        // DELETE, then the lookup is a domain miss (empty), while a second delete is an error.
        Long createdId = created.id();
        announcements.delete(createdId);
        assertThat(announcements.readOne(createdId)).isEmpty();
        assertThatThrownBy(() -> announcements.delete(createdId))
            .isInstanceOf(DalClientNotFoundException.class);
    }

    @Test
    void compositeIdLifecycleProvesCodecParityWithTheServer() {
        TranslationId id = new TranslationId("client.it." + UUID.randomUUID(), "en");

        // CREATE via DTO with the composite id as nested JSON (nested-path payload).
        Translation created = translations.create(new Translation(id, "Hello"));
        assertThat(created.id()).isEqualTo(id);
        assertThat(created.value()).isEqualTo("Hello");

        // READ ONE / UPDATE / DELETE address the row by the client-encoded Base64 id — the exact
        // inverse of the server's DalIdArgumentResolver decode (shared DalIdCodec).
        assertThat(translations.readOne(id))
            .hasValueSatisfying(fetched -> assertThat(fetched.value()).isEqualTo("Hello"));

        assertThat(translations.update(id, Map.of("value", "Hi")))
            .hasValueSatisfying(updated -> assertThat(updated.value()).isEqualTo("Hi"));

        translations.delete(id);
        assertThat(translations.readOne(id)).isEmpty();
    }

    @Test
    void filteredAndSortedPageRead() {
        String marker = "client-page-" + UUID.randomUUID();
        announcements.create(new Announcement(null, marker + "-b", "Second.", "INFO"));
        announcements.create(new Announcement(null, marker + "-a", "First.", "INFO"));

        DalPage<Announcement> page = announcements.read(
            "title ~ '" + marker + "*'",
            DalPageRequest.of(0, 10).withSort(DalSort.asc("title")));

        assertThat(page.page().totalElements()).isEqualTo(2);
        assertThat(page.content())
            .extracting(Announcement::title)
            .containsExactly(marker + "-a", marker + "-b");
    }

    @Test
    void validationFailureSurfacesFieldErrors() {
        Announcement blankTitle = new Announcement(null, "", "Blank title must be rejected.", "INFO");
        assertThatThrownBy(() -> announcements.create(blankTitle))
            .isInstanceOf(DalClientValidationException.class)
            .satisfies(ex -> {
                DalClientValidationException validation = (DalClientValidationException) ex;
                assertThat(validation.statusCode()).isEqualTo(400);
                assertThat(validation.errors())
                    .anySatisfy(error -> assertThat(error.field()).isEqualTo("title"));
            });
    }

    @Test
    void unpagedReadUsesServerDefaults() {
        DalPage<Announcement> page = announcements.read(DalPageRequest.unpaged());

        assertThat(page.page().size()).isEqualTo(20);
        assertThat(page.page().number()).isZero();
        assertThat(List.copyOf(page.content())).hasSizeLessThanOrEqualTo(20);
    }
}
