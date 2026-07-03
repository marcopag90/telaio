package io.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification that the Telaio OpenAPI documentation is served correctly: booting the whole
 * showcase over real HTTP, the branded {@code TELAIO} group must expose concrete CRUD operations and
 * schemas for every registered DAL (replacing the generic templated controller operations), while a
 * consumer's own group ({@code ACTUATOR}) stays free of DAL operations.
 */
class OpenApiDocsIT extends AbstractShowcaseIT {

    private static final List<String> DAL_NAMES =
        List.of("announcements", "employees", "departments", "articles", "products", "translations");

    /**
     * DALs that expose the full CRUD surface (no {@code @DalService(operations = …)} restriction):
     * {@code POST}/{@code GET} on the collection and {@code GET}/{@code PATCH}/{@code DELETE} on the item.
     */
    private static final List<String> FULL_CRUD_DALS =
        List.of("announcements", "employees", "departments", "products", "translations");

    @Test
    void telaioGroup_shouldExposeEachDalWithItsConfiguredOperations() {
        JsonNode paths = apiDocs("TELAIO").get("paths");

        for (String dal : FULL_CRUD_DALS) {
            JsonNode collection = paths.get("/dal/v1/%s".formatted(dal));
            assertThat(collection).as("collection path for '%s'", dal).isNotNull();
            assertThat(collection.has("post")).as("POST /dal/v1/%s", dal).isTrue();
            assertThat(collection.has("get")).as("GET /dal/v1/%s", dal).isTrue();

            JsonNode item = paths.get("/dal/v1/%s/{id}".formatted(dal));
            assertThat(item).as("item path for '%s'", dal).isNotNull();
            assertThat(item.has("get")).as("GET /dal/v1/%s/{id}", dal).isTrue();
            assertThat(item.has("patch")).as("PATCH /dal/v1/%s/{id}", dal).isTrue();
            assertThat(item.has("delete")).as("DELETE /dal/v1/%s/{id}", dal).isTrue();
        }

        // 'articles' is read-only by exposure (@DalService(operations = {READ, READ_ONE})): only the two
        // read operations are documented; the write operations are structurally absent from the document.
        JsonNode articlesCollection = paths.get("/dal/v1/articles");
        assertThat(articlesCollection).as("collection path for 'articles'").isNotNull();
        assertThat(articlesCollection.has("get")).as("GET /dal/v1/articles").isTrue();
        assertThat(articlesCollection.has("post")).as("POST /dal/v1/articles must be absent").isFalse();

        JsonNode articlesItem = paths.get("/dal/v1/articles/{id}");
        assertThat(articlesItem).as("item path for 'articles'").isNotNull();
        assertThat(articlesItem.has("get")).as("GET /dal/v1/articles/{id}").isTrue();
        assertThat(articlesItem.has("patch")).as("PATCH /dal/v1/articles/{id} must be absent").isFalse();
        assertThat(articlesItem.has("delete")).as("DELETE /dal/v1/articles/{id} must be absent").isFalse();

        // The opaque generic operations springdoc derives from the dynamic controller are replaced.
        assertThat(paths.has("/dal/v1/{dalName}")).isFalse();
        assertThat(paths.has("/dal/v1/{dalName}/{id}")).isFalse();
    }

    @Test
    void telaioGroup_shouldRegisterEntitySchemas() {
        JsonNode schemas = apiDocs("TELAIO").get("components").get("schemas");

        assertThat(schemas.has("Announcement")).isTrue();
        assertThat(schemas.has("Employee")).isTrue();
        assertThat(schemas.has("Department")).isTrue();
        assertThat(schemas.has("Article")).isTrue();
        assertThat(schemas.has("Product")).isTrue();
    }

    @Test
    void telaioGroup_shouldNotExposeOrphanControllerTag() {
        // The dynamic controller's @Tag("DAL API v1") must not survive as an empty section.
        assertThat(apiDocs("TELAIO").path("tags").toString()).doesNotContain("DAL API v1");
    }

    @Test
    void employeeSchema_shouldHonorJacksonReadWriteAccess() {
        JsonNode employee = apiDocs("TELAIO")
            .get("components").get("schemas").get("Employee").get("properties");

        // Read-only @ManyToOne relation (a $ref property) stays out of the request example.
        assertThat(employee.get("department").path("readOnly").asBoolean(false)).isTrue();
        // Write-only foreign key stays out of responses.
        assertThat(employee.get("departmentId").path("writeOnly").asBoolean(false)).isTrue();
    }

    @Test
    void compositeIdDal_shouldDocumentBase64IdParamWithExample() {
        JsonNode doc = apiDocs("TELAIO");

        JsonNode idParam = null;
        for (JsonNode parameter : doc.get("paths").get("/dal/v1/translations/{id}").get("get").get("parameters")) {
            if ("id".equals(parameter.get("name").asString())) {
                idParam = parameter;
                break;
            }
        }
        assertThat(idParam).as("id path parameter on translations").isNotNull();
        assertThat(idParam.get("description").asString()).contains("Base64");
        // A copy-pasteable Base64 example so the composite-id endpoint is invokable from Swagger UI.
        assertThat(idParam.get("schema").has("example")).isTrue();

        // The composite id type is registered as its own schema.
        assertThat(doc.get("components").get("schemas").has("TranslationId")).isTrue();
    }

    @Test
    void actuatorGroup_shouldNotLeakDalOperations() {
        JsonNode paths = apiDocs("ACTUATOR").path("paths");

        for (String dal : DAL_NAMES) {
            assertThat(paths.has("/dal/v1/%s".formatted(dal)))
                .as("DAL '%s' must not leak into the ACTUATOR group", dal)
                .isFalse();
        }
    }

    /**
     * Fetches a springdoc group document anonymously ({@code /v3/api-docs/**} is permit-all).
     */
    private JsonNode apiDocs(String group) {
        ResponseEntity<String> response = exchange(
            null, HttpMethod.GET, "http://localhost:%d/v3/api-docs/%s".formatted(port, group), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return tree(response);
    }
}
