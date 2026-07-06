package com.paganbit.telaio.openapi.introspection;

import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.openapi.fixture.Order;
import com.paganbit.telaio.openapi.fixture.Product;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DalEntitySchemaResolver}.
 */
class DalEntitySchemaResolverTest {

    private final DalEntitySchemaResolver resolver = new DalEntitySchemaResolver();

    @Test
    void ensureComponents_shouldInitializeComponentsAndSchemaMap() {
        OpenAPI openApi = new OpenAPI();

        resolver.ensureComponents(openApi);

        assertThat(openApi.getComponents()).isNotNull();
        assertThat(openApi.getComponents().getSchemas()).isNotNull();
    }

    @Test
    void resolveAndRegister_shouldRegisterSchemaAndReturnRef() {
        OpenAPI openApi = new OpenAPI();

        Schema<?> ref = resolver.resolveAndRegister(Product.class, openApi);

        assertThat(ref.get$ref()).contains("Product");
        Schema<?> productSchema = schemaContaining(openApi);
        assertThat(productSchema.getProperties()).containsKey("cost_price");
        assertThat(productSchema.getProperties()).doesNotContainKey("costPrice");
    }

    @Test
    void resolveAndRegister_shouldNotOverwriteExistingSchema() {
        String key = schemaKeyContaining(resolveInto(new OpenAPI()));

        OpenAPI openApi = new OpenAPI();
        resolver.ensureComponents(openApi);
        StringSchema marker = new StringSchema();
        marker.setDescription("MARKER");
        openApi.getComponents().getSchemas().put(key, marker);

        resolver.resolveAndRegister(Product.class, openApi);

        assertThat(openApi.getComponents().getSchemas().get(key)).isSameAs(marker);
    }

    private OpenAPI resolveInto(OpenAPI openApi) {
        resolver.resolveAndRegister(Product.class, openApi);
        return openApi;
    }

    private static Schema<?> schemaContaining(OpenAPI openApi) {
        return openApi.getComponents().getSchemas().get(schemaKeyContaining(openApi));
    }

    private static String schemaKeyContaining(OpenAPI openApi) {
        return openApi.getComponents().getSchemas().keySet().stream()
            .filter(name -> name.contains("Product"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No schema registered containing: " + "Product"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void resolveAndRegister_shouldPopulateSchemaProperties() {
        OpenAPI openApi = new OpenAPI();

        resolver.resolveAndRegister(Product.class, openApi);

        Map<String, Schema> properties = schemaContaining(openApi).getProperties();
        assertThat(properties).containsKeys("id", "name", "price", "cost_price");
    }

    @Test
    void resolveAndRegister_shouldApplyJacksonAccessToEntitySchema() {
        OpenAPI openApi = new OpenAPI();
        DalEntitySchemaResolver accessAware =
            new DalEntitySchemaResolver(new JsonPropertyPathResolver(JsonMapper.builder().build()));

        accessAware.resolveAndRegister(Order.class, openApi);

        Schema<?> order = openApi.getComponents().getSchemas().get("Order");
        // Relation ($ref) — the case swagger-core drops; our post-processing restores readOnly.
        assertThat(order.getProperties().get("customer").getReadOnly()).isTrue();
        // Write-only foreign key.
        assertThat(order.getProperties().get("customerId").getWriteOnly()).isTrue();
        // Unannotated field keeps default read/write semantics.
        assertThat(order.getProperties().get("code").getReadOnly()).isNull();
        assertThat(order.getProperties().get("code").getWriteOnly()).isNull();
    }

    @Test
    void resolveAndRegister_withoutPathResolver_shouldNotMarkRelationAccess() {
        OpenAPI openApi = new OpenAPI();

        // The no-arg resolver skips access post-processing, exposing swagger-core's $ref gap.
        resolver.resolveAndRegister(Order.class, openApi);

        Schema<?> order = openApi.getComponents().getSchemas().get("Order");
        assertThat(order.getProperties().get("customer").getReadOnly()).isNull();
    }
}
