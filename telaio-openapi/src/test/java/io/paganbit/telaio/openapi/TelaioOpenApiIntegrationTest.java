package io.paganbit.telaio.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying that, in a real servlet context with springdoc, the
 * {@code DalOpenApiCustomizer} is discovered and applied: the served default OpenAPI document
 * ({@code GET /v3/api-docs}) contains concrete per-DAL operations and schemas, and the generic templated
 * operations of the dynamic controller are gone. Boots without a database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = OpenApiTestApplication.class)
@AutoConfigureMockMvc
class TelaioOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void apiDocs_shouldContainSynthesizedPerDalOperations() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        assertThat(paths.has("/dal/v1/products")).isTrue();
        assertThat(paths.has("/dal/v1/products/{id}")).isTrue();
        assertThat(paths.has("/dal/v1/notes")).isTrue();
        assertThat(paths.has("/dal/v1/notes/{id}")).isTrue();

        // The generic templated operations springdoc derives from the dynamic controller are removed.
        assertThat(paths.has("/dal/v1/{dalName}")).isFalse();
        assertThat(paths.has("/dal/v1/{dalName}/{id}")).isFalse();
    }

    @Test
    void apiDocs_shouldRegisterEntityAndErrorSchemas() throws Exception {
        JsonNode schemas = apiDocs().get("components").get("schemas");

        assertThat(schemas.has("Product")).isTrue();
        assertThat(schemas.has("Note")).isTrue();
        assertThat(schemas.has("ProblemDetail")).isTrue();
        // Jackson @JsonProperty is honored end-to-end: the wire name, not the Java name.
        assertThat(schemas.get("Product").get("properties").has("cost_price")).isTrue();
        assertThat(schemas.get("Product").get("properties").has("costPrice")).isFalse();
    }

    @Test
    void apiDocs_shouldExposeFilterPaginationAndIdParameters() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        assertThat(parameterNames(paths.get("/dal/v1/products").get("get")))
            .contains("q", "page", "size", "sort");
        assertThat(parameterNames(paths.get("/dal/v1/products/{id}").get("get")))
            .contains("id");
    }

    @Test
    void apiDocs_shouldTagOperationsPerDalCapitalized() throws Exception {
        JsonNode tags = apiDocs()
            .get("paths").get("/dal/v1/products").get("post").get("tags");

        assertThat(tags.toString()).contains("Products").doesNotContain("\"products\"");
    }

    @Test
    void apiDocs_shouldNotExposeOrphanControllerTag() throws Exception {
        JsonNode tags = apiDocs().path("tags");

        // The dynamic controller's @Tag("DAL API v1") must not survive as an empty section.
        assertThat(tags.toString()).doesNotContain("DAL API v1");
    }

    @Test
    void apiDocs_shouldMarkReadOnlyRelationAndWriteOnlyForeignKey() throws Exception {
        JsonNode order = apiDocs().get("components").get("schemas").get("Order").get("properties");

        // Served end-to-end: readOnly survives next to a $ref (relation), writeOnly on the FK.
        assertThat(order.get("customer").path("readOnly").asBoolean(false)).isTrue();
        assertThat(order.get("customerId").path("writeOnly").asBoolean(false)).isTrue();
    }

    private JsonNode apiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return json.readTree(body);
    }

    private static List<String> parameterNames(JsonNode operation) {
        List<String> names = new ArrayList<>();
        operation.get("parameters").forEach(parameter -> names.add(parameter.get("name").asString()));
        return names;
    }
}
