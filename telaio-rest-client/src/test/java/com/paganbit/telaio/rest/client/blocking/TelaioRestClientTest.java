package com.paganbit.telaio.rest.client.blocking;

import com.paganbit.telaio.rest.client.DalPage;
import com.paganbit.telaio.rest.client.DalPageRequest;
import com.paganbit.telaio.rest.client.DalSort;
import com.paganbit.telaio.rest.client.blocking.v1.DalClient;
import com.paganbit.telaio.rest.client.exception.DalClientEncodingException;
import com.paganbit.telaio.rest.client.exception.DalClientNotFoundException;
import com.paganbit.telaio.rest.client.exception.DalClientTransportException;
import com.paganbit.telaio.rest.client.exception.DalClientValidationException;
import com.turkraft.springfilter.language.GreaterThanOperator;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import com.turkraft.springfilter.parser.node.InputNode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelaioRestClientTest {

    private static final String BASE = "http://remote-app";

    record Product(@Nullable Long id, @Nullable String name, @Nullable Double price) {
    }

    record TranslationId(String language, String code) {
    }

    record Translation(@Nullable TranslationId id, @Nullable String text) {
    }

    private MockRestServiceServer server;
    private DalClient<Product, Long> products;
    private DalClient<Translation, TranslationId> translations;

    @BeforeEach
    void setUp() {
        // The RestClient is composed by the caller with Spring's own API: base URL included.
        RestClient.Builder restClientBuilder = RestClient.builder()
            .baseUrl(BASE)
            .defaultHeader("X-Test", "on");
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        TelaioRestClient client = TelaioRestClient.create(restClientBuilder.build());
        products = client.dal("products", Product.class, Long.class);
        translations = client.dal("translations", Translation.class, TranslationId.class);
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void createPostsPayloadAndReturnsCreatedEntity() {
        server.expect(requestTo(BASE + "/dal/v1/products"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Test", "on"))
            .andExpect(header("Content-Type", "application/json"))
            .andExpect(content().json("""
                {"name": "alpha", "price": 10.5}"""))
            .andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {"id": 1, "name": "alpha", "price": 10.5}"""));

        Product created = products.create(Map.of("name", "alpha", "price", 10.5));

        assertThat(created).isEqualTo(new Product(1L, "alpha", 10.5));
    }

    @Test
    void dtoPayloadDropsNullsWhileMapPayloadPreservesThem() {
        server.expect(requestTo(BASE + "/dal/v1/products"))
            .andExpect(method(HttpMethod.POST))
            // DTO: null id/price must NOT be on the wire.
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("price"))))
            .andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {"id": 1, "name": "alpha"}"""));
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andExpect(method(HttpMethod.PATCH))
            // Map: the explicit null must survive (merge-patch null-set).
            .andExpect(content().json("""
                {"price": null}"""))
            .andRespond(withSuccess("""
                {"id": 1, "name": "alpha"}""", MediaType.APPLICATION_JSON));

        products.create(new Product(null, "alpha", null));

        Map<String, @Nullable Object> patch = new HashMap<>();
        patch.put("price", null);
        products.update(1L, patch);
    }

    @Test
    void readWithFilterTreeSendsTheRenderedExpression() {
        server.expect(requestTo(BASE + "/dal/v1/products?q=price%20%3E%20%275%27&page=0&size=10"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                    {"content": [{"id": 1, "name": "alpha", "price": 10.5}],
                     "page": {"size": 10, "number": 0, "totalElements": 1, "totalPages": 1}}""",
                MediaType.APPLICATION_JSON));

        DalPage<Product> page = products.read(
            new FieldNode("price").infix(new GreaterThanOperator(), new InputNode(5)),
            DalPageRequest.of(0, 10));

        assertThat(page.content()).containsExactly(new Product(1L, "alpha", 10.5));
    }

    @Test
    void readSendsFilterAndPagingAndParsesPage() {
        server.expect(requestTo(BASE + "/dal/v1/products?q=price%3E5&page=0&size=10&sort=name,asc"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                    {"content": [{"id": 1, "name": "alpha", "price": 10.5}],
                     "page": {"size": 10, "number": 0, "totalElements": 1, "totalPages": 1}}""",
                MediaType.APPLICATION_JSON));

        DalPage<Product> page = products.read("price>5",
            DalPageRequest.of(0, 10).withSort(DalSort.asc("name")));

        assertThat(page.content()).containsExactly(new Product(1L, "alpha", 10.5));
        assertThat(page.page().totalElements()).isEqualTo(1);
    }

    @Test
    void readWithNullFilterTreeReadsUnfiltered() {
        // The FilterNode overload with a null tree sends no q parameter (mirrors read(null, ...)).
        server.expect(requestTo(BASE + "/dal/v1/products?page=0&size=5"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                    {"content": [{"id": 1, "name": "alpha", "price": 10.5}],
                     "page": {"size": 5, "number": 0, "totalElements": 1, "totalPages": 1}}""",
                MediaType.APPLICATION_JSON));

        DalPage<Product> page = products.read((FilterNode) null, DalPageRequest.of(0, 5));

        assertThat(page.content()).containsExactly(new Product(1L, "alpha", 10.5));
    }

    @Test
    void readWithoutFilterUsesThePagingOnlyDefaultOverload() {
        // Exercises the default read(DalPageRequest) method: no filter, so no q parameter.
        server.expect(requestTo(BASE + "/dal/v1/products?page=0&size=10"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                    {"content": [{"id": 1, "name": "alpha", "price": 10.5}],
                     "page": {"size": 10, "number": 0, "totalElements": 1, "totalPages": 1}}""",
                MediaType.APPLICATION_JSON));

        DalPage<Product> page = products.read(DalPageRequest.of(0, 10));

        assertThat(page.content()).containsExactly(new Product(1L, "alpha", 10.5));
    }

    @Test
    void readOneReturnsEntity() {
        server.expect(requestTo(BASE + "/dal/v1/products/42"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {"id": 42, "name": "alpha", "price": 10.5}""", MediaType.APPLICATION_JSON));

        assertThat(products.readOne(42L)).contains(new Product(42L, "alpha", 10.5));
    }

    @Test
    void readOneMapsProblemNotFoundToEmpty() {
        server.expect(requestTo(BASE + "/dal/v1/products/42"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("""
                    {"status": 404, "detail": "Resource not found with ID: 42"}"""));

        assertThat(products.readOne(42L)).isEmpty();
    }

    @Test
    void readOneToleratesRbacStrippedAndUnknownFields() {
        server.expect(requestTo(BASE + "/dal/v1/products/42"))
            .andRespond(withSuccess("""
                {"id": 42, "newServerField": "ignored"}""", MediaType.APPLICATION_JSON));

        assertThat(products.readOne(42L)).contains(new Product(42L, null, null));
    }

    @Test
    void compositeIdTravelsAsUrlSafeBase64Json() {
        TranslationId id = new TranslationId("it", "GREETING");
        String expectedSegment = Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"language\":\"it\",\"code\":\"GREETING\"}".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(BASE + "/dal/v1/translations/" + expectedSegment))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                    {"id": {"language": "it", "code": "GREETING"}, "text": "Ciao"}""",
                MediaType.APPLICATION_JSON));

        assertThat(translations.readOne(id))
            .contains(new Translation(id, "Ciao"));
    }

    @Test
    void updateReturnsEntityOn200AndEmptyOn204() {
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andExpect(method(HttpMethod.PATCH))
            .andRespond(withSuccess("""
                {"id": 1, "name": "renamed", "price": 10.5}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andExpect(method(HttpMethod.PATCH))
            .andRespond(withNoContent());

        Optional<Product> updated = products.update(1L, Map.of("name", "renamed"));
        assertThat(updated).contains(new Product(1L, "renamed", 10.5));

        assertThat(products.update(1L, Map.of("name", "hidden"))).isEmpty();
    }

    @Test
    void updateOnMissingEntityThrowsNotFound() {
        server.expect(requestTo(BASE + "/dal/v1/products/99"))
            .andExpect(method(HttpMethod.PATCH))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("""
                    {"status": 404, "detail": "Resource not found with ID: 99"}"""));

        var patch = Map.of("name", "x");
        assertThatThrownBy(() -> products.update(99L, patch))
            .isInstanceOf(DalClientNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    void deleteSucceedsOn204() {
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withNoContent());

        products.delete(1L);
    }

    @Test
    void createValidationFailureCarriesErrors() {
        server.expect(requestTo(BASE + "/dal/v1/products"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("""
                    {"status": 400, "detail": "Validation failed",
                     "errors": [{"object": "product", "field": "name", "message": "must not be blank"}]}"""));

        var payload = Map.of("price", 1);
        assertThatThrownBy(() -> products.create(payload))
            .isInstanceOf(DalClientValidationException.class)
            .satisfies(ex -> {
                DalClientValidationException validation = (DalClientValidationException) ex;
                assertThat(validation.errors()).hasSize(1);
                assertThat(validation.errors().getFirst().field()).isEqualTo("name");
            });
    }

    @Test
    void deleteDrainsAndIgnoresAnInjectedSuccessBody() {
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andExpect(method(HttpMethod.DELETE))
            // E.g. a proxy answering 200 with a text body instead of the server's bare 204.
            .andRespond(withSuccess("Deleted", MediaType.TEXT_PLAIN));

        products.delete(1L);
    }

    @Test
    void nonJsonSuccessBodyRaisesMalformedResponse() {
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("<html>captive portal</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> products.readOne(1L))
            .isInstanceOf(com.paganbit.telaio.rest.client.exception.DalClientMalformedResponseException.class)
            .hasMessageContaining("JSON");
    }

    @Test
    void requestsCarryTheJsonAcceptHeader() {
        server.expect(requestTo(BASE + "/dal/v1/products/42"))
            .andExpect(header("Accept",
                org.hamcrest.Matchers.containsString("application/json")))
            .andRespond(withSuccess("""
                {"id": 42}""", MediaType.APPLICATION_JSON));

        products.readOne(42L);
    }

    @Test
    void restClientWithoutBaseUrlFailsOnFirstCallWithDescriptiveMessage() {
        // Spring exposes no getter on a built RestClient, so create() cannot validate eagerly:
        // the failure must surface on the first invocation, with a remediation hint.
        // NOTE: this classpath has no httpclient5, so the JDK request factory rejects the
        // relative URI at request-creation time; with Apache HttpComponents the same condition
        // surfaces at execution time and is matched by the cause-chain heuristic instead.
        TelaioRestClient noBase = TelaioRestClient.create(RestClient.create());
        final var dal = noBase.dal("products", Product.class, Long.class);

        assertThatThrownBy(() -> dal.readOne(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("base URL");
    }

    @Test
    void ioFailureIsWrappedAsTransportException() {
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> products.readOne(1L))
            .isInstanceOf(DalClientTransportException.class)
            .hasMessageContaining("products");
    }

    @Test
    void createWithEmptySuccessBodyRaisesMalformedResponse() {
        server.expect(requestTo(BASE + "/dal/v1/products"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CREATED));

        var payload = Map.of("name", "alpha");
        assertThatThrownBy(() -> products.create(payload))
            .isInstanceOf(com.paganbit.telaio.rest.client.exception.DalClientMalformedResponseException.class)
            .hasMessageContaining("no entity body");
    }

    @Test
    void executionTimeMissingBaseUrlSurfacesAsIllegalState() {
        // Some request factories (e.g. Apache HttpComponents) reject a relative URI only at
        // execution time, surfacing it as an I/O failure the cause-chain heuristic recognizes.
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andRespond(withException(new IOException("URI is not absolute")));

        assertThatThrownBy(() -> products.readOne(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("base URL");
    }

    @Test
    void missingHostIoFailureIsAlsoRecognizedAsMissingBaseUrl() {
        // Apache HttpComponents phrasing of the same relative-URI condition.
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andRespond(withException(new IOException("does not specify a valid host name")));

        assertThatThrownBy(() -> products.readOne(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("base URL");
    }

    @Test
    void transportFailureWithNullMessageIsStillWrapped() {
        // A cause with no message must not break the base-URL heuristic's cause-chain scan.
        server.expect(requestTo(BASE + "/dal/v1/products/1"))
            .andRespond(withException(new IOException()));

        assertThatThrownBy(() -> products.readOne(1L))
            .isInstanceOf(DalClientTransportException.class)
            .hasMessageContaining("products");
    }

    @Test
    void idEncodingFailureRaisesEncodingException() {
        // A complex id whose JSON serialization fails never reaches the wire: the encode step
        // wraps the codec failure as a caller-side DalClientEncodingException. No request is
        // issued, so the failure is independent of any mocked server expectation.
        DalClient<Product, ExplodingId> exploding = TelaioRestClient
            .create(RestClient.builder().baseUrl(BASE).build())
            .dal("products", Product.class, ExplodingId.class);
        ExplodingId id = new ExplodingId("x");

        assertThatThrownBy(() -> exploding.readOne(id))
            .isInstanceOf(DalClientEncodingException.class)
            .hasMessageContaining("products");
    }

    /**
     * A complex (record) id whose accessor throws while Jackson serializes it.
     */
    record ExplodingId(String code) {
        @Override
        public String code() {
            throw new IllegalStateException("cannot read id component");
        }
    }
}
