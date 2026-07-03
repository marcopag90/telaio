package io.paganbit.telaio.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for grouped mode: with {@code telaio.web.openapi.enabled=true} the branded {@code TELAIO}
 * {@code GroupedOpenApi} is declared, so springdoc serves its document at {@code /v3/api-docs/TELAIO}. This
 * verifies the global {@code DalOpenApiCustomizer} also applies inside the branded group — exactly the
 * showcase configuration.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = OpenApiTestApplication.class,
    properties = "telaio.web.openapi.enabled=true"
)
@AutoConfigureMockMvc
class TelaioOpenApiGroupedIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void telaioGroupDoc_shouldContainSynthesizedPerDalOperations() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs/TELAIO"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode doc = json.readTree(body);
        JsonNode paths = doc.get("paths");

        assertThat(paths.has("/dal/v1/products")).isTrue();
        assertThat(paths.has("/dal/v1/products/{id}")).isTrue();
        assertThat(paths.has("/dal/v1/notes")).isTrue();
        assertThat(paths.has("/dal/v1/{dalName}")).isFalse();

        // The orphan controller @Tag must not survive in the TELAIO group (Swagger UI primaryName=TELAIO).
        assertThat(doc.path("tags").toString()).doesNotContain("DAL API v1");
    }
}
