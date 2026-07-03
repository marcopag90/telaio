package io.paganbit.telaio.openapi;

import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the key design property: the synthesized per-DAL operations must appear <em>only</em> in the
 * branded {@code TELAIO} group, never in a consuming application's own {@link GroupedOpenApi} groups. With a
 * user-defined {@code OTHER} group present alongside the TELAIO group, the DAL paths must be in TELAIO and
 * absent from OTHER.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = OpenApiTestApplication.class,
    properties = "telaio.web.openapi.enabled=true"
)
@AutoConfigureMockMvc
@Import(TelaioOpenApiGroupIsolationTest.UserGroupConfig.class)
class TelaioOpenApiGroupIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void dalOperations_shouldAppearInTelaioGroupOnly() throws Exception {
        JsonNode telaioPaths = apiDocs("/v3/api-docs/TELAIO").path("paths");
        assertThat(telaioPaths.has("/dal/v1/products")).isTrue();
        assertThat(telaioPaths.has("/dal/v1/notes")).isTrue();

        // The consumer's own group must stay clean — no DAL operations leak into it.
        JsonNode otherPaths = apiDocs("/v3/api-docs/OTHER").path("paths");
        assertThat(otherPaths.has("/dal/v1/products")).isFalse();
        assertThat(otherPaths.has("/dal/v1/notes")).isFalse();
        assertThat(otherPaths.has("/dal/v1/{dalName}")).isFalse();
    }

    private JsonNode apiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return json.readTree(body);
    }

    /**
     * A consumer-defined OpenAPI group, unrelated to the DAL API.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class UserGroupConfig {

        @Bean
        GroupedOpenApi otherGroup() {
            return GroupedOpenApi.builder()
                .group("OTHER")
                .pathsToMatch("/other/**")
                .build();
        }
    }
}
