package com.paganbit.telaio.openapi.introspection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.openapi.fixture.Order;
import com.paganbit.telaio.openapi.fixture.Product;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
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

    @Test
    void resolveAndRegister_withPathResolver_shouldSkipAccessMarkingForInlineSchema() {
        OpenAPI openApi = new OpenAPI();
        DalEntitySchemaResolver accessAware =
            new DalEntitySchemaResolver(new JsonPropertyPathResolver(JsonMapper.builder().build()));

        // String resolves to an inline schema without a $ref, so access post-processing is skipped.
        Schema<?> schema = accessAware.resolveAndRegister(String.class, openApi);

        assertThat(schema.get$ref()).isNull();
        assertThat(schema.getType()).isEqualTo("string");
    }

    @Test
    void resolveAndRegister_withPathResolver_shouldSkipAccessMarkingWhenExistingSchemaHasNoProperties() {
        OpenAPI openApi = new OpenAPI();
        DalEntitySchemaResolver accessAware =
            new DalEntitySchemaResolver(new JsonPropertyPathResolver(JsonMapper.builder().build()));
        accessAware.ensureComponents(openApi);
        // A pre-registered schema with no properties wins over the resolved one (putIfAbsent) and
        // must short-circuit the access marking without error.
        ObjectSchema marker = new ObjectSchema();
        marker.setDescription("MARKER");
        openApi.getComponents().getSchemas().put("Order", marker);

        accessAware.resolveAndRegister(Order.class, openApi);

        Schema<?> registered = openApi.getComponents().getSchemas().get("Order");
        assertThat(registered).isSameAs(marker);
        assertThat(registered.getProperties()).isNull();
    }

    @Test
    void resolveAndRegister_withPathResolver_shouldIgnorePropertyMissingFromExistingSchema() {
        OpenAPI openApi = new OpenAPI();
        DalEntitySchemaResolver accessAware =
            new DalEntitySchemaResolver(new JsonPropertyPathResolver(JsonMapper.builder().build()));
        accessAware.ensureComponents(openApi);
        // The pre-registered schema carries only a property unrelated to the annotated fields, so the
        // JSON name resolves but the schema lookup misses and access marking is skipped.
        ObjectSchema marker = new ObjectSchema();
        marker.addProperty("unrelated", new StringSchema());
        openApi.getComponents().getSchemas().put("Order", marker);

        accessAware.resolveAndRegister(Order.class, openApi);

        Schema<?> registered = openApi.getComponents().getSchemas().get("Order");
        assertThat(registered).isSameAs(marker);
        assertThat(registered.getProperties()).containsOnlyKeys("unrelated");
        assertThat(registered.getProperties().get("unrelated").getReadOnly()).isNull();
        assertThat(registered.getProperties().get("unrelated").getWriteOnly()).isNull();
    }

    @Test
    void resolveAndRegister_withPathResolver_shouldGiveUpWhenFieldIsIgnoredInBothViews() {
        OpenAPI openApi = new OpenAPI();
        DalEntitySchemaResolver accessAware =
            new DalEntitySchemaResolver(new JsonPropertyPathResolver(JsonMapper.builder().build()));

        // Both annotated fields are ignored, so their JSON names resolve in neither the serialization
        // nor the deserialization view and access marking gives up quietly.
        Schema<?> ref = accessAware.resolveAndRegister(IgnoredAccessEntity.class, openApi);

        assertThat(ref.get$ref()).contains("IgnoredAccessEntity");
        Schema<?> schema = openApi.getComponents().getSchemas().get("IgnoredAccessEntity");
        assertThat(schema.getProperties()).containsKey("label");
        assertThat(schema.getProperties()).doesNotContainKeys("secretRead", "secretWrite");
    }

    @Test
    void resolveAndRegister_withPathResolver_shouldHandleInterfaceEntity() {
        OpenAPI openApi = new OpenAPI();
        DalEntitySchemaResolver accessAware =
            new DalEntitySchemaResolver(new JsonPropertyPathResolver(JsonMapper.builder().build()));

        // An interface has no superclass: the field walk ends on null and the constant is skipped.
        Schema<?> ref = accessAware.resolveAndRegister(NamedEntity.class, openApi);

        assertThat(ref.get$ref()).contains("NamedEntity");
        assertThat(openApi.getComponents().getSchemas()).containsKey("NamedEntity");
    }

    @Test
    void resolveAndRegister_withPathResolver_shouldSkipSyntheticFieldOfInnerClassEntity() {
        OpenAPI openApi = new OpenAPI();
        DalEntitySchemaResolver accessAware =
            new DalEntitySchemaResolver(new JsonPropertyPathResolver(JsonMapper.builder().build()));

        // The non-static inner entity carries a compiler-generated this$0 field, which must be skipped.
        Schema<?> ref = accessAware.resolveAndRegister(InnerEntity.class, openApi);

        assertThat(ref.get$ref()).contains("InnerEntity");
        Schema<?> schema = openApi.getComponents().getSchemas().get("InnerEntity");
        assertThat(schema.getProperties().get("name").getReadOnly()).isTrue();
    }

    /**
     * Entity whose access-annotated fields are also ignored, so neither Jackson view can resolve
     * their JSON names.
     */
    @SuppressWarnings("unused")
    static class IgnoredAccessEntity {

        @JsonIgnore
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String secretRead;

        @JsonIgnore
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String secretWrite;

        private String label;

        public String getSecretRead() {
            return secretRead;
        }

        public String getSecretWrite() {
            return secretWrite;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * Interface entity: no superclass to walk and a constant (static field) to skip.
     */
    @SuppressWarnings("unused")
    interface NamedEntity {

        String CONSTANT = "x";

        String getName();
    }

    /**
     * Non-static inner entity: its compiler-generated {@code this$0} field is synthetic. The outer
     * reference below is deliberate — without it the compiler omits the {@code this$0} field entirely.
     */
    @SuppressWarnings("unused")
    class InnerEntity {

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String name;

        public String getName() {
            return name;
        }

        private Object outer() {
            return DalEntitySchemaResolverTest.this;
        }
    }
}
