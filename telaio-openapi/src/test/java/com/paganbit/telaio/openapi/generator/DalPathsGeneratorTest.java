package com.paganbit.telaio.openapi.generator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.openapi.fixture.CompositeId;
import com.paganbit.telaio.openapi.fixture.Product;
import com.paganbit.telaio.openapi.introspection.DalEntitySchemaResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DalPathsGenerator}.
 */
class DalPathsGeneratorTest {

    private static final String COLLECTION_PATH = "/dal/v1/products";
    private static final String ITEM_PATH = "/dal/v1/products/{id}";

    private final DalEntitySchemaResolver schemaResolver = new DalEntitySchemaResolver();

    private DalPathsGenerator generator(boolean tagPerDal) {
        FilterParameterDescriber describer =
            new FilterParameterDescriber(new JsonPropertyPathResolver(JsonMapper.builder().build()), true);
        return new DalPathsGenerator(schemaResolver, describer, JsonMapper.builder().build(), tagPerDal);
    }

    private OpenAPI generate(boolean tagPerDal, Class<?> idClass) {
        return generate(tagPerDal, idClass, EnumSet.allOf(DalOperationType.class));
    }

    private OpenAPI generate(boolean tagPerDal, Class<?> idClass, Set<DalOperationType> exposedOperations) {
        OpenAPI openApi = new OpenAPI().paths(new Paths());
        Schema<Object> problemDetailRef = new Schema<>();
        problemDetailRef.set$ref("#/components/schemas/ProblemDetail");
        generator(tagPerDal).generate(
            openApi, "products", Product.class, idClass, problemDetailRef, exposedOperations);
        return openApi;
    }

    @Test
    void generate_shouldAddCollectionAndItemPaths() {
        OpenAPI openApi = generate(true, Long.class);

        assertThat(openApi.getPaths()).containsKeys(COLLECTION_PATH, ITEM_PATH);
    }

    @Test
    void generate_shouldBuildCreateAndReadOnCollection() {
        PathItem collection = generate(true, Long.class).getPaths().get(COLLECTION_PATH);

        Operation create = collection.getPost();
        assertThat(create.getOperationId()).isEqualTo("create_products");
        assertThat(create.getRequestBody().getContent()).containsKey("application/json");
        assertThat(create.getResponses()).containsKeys("201", "400", "403", "404", "500");
        assertErrorBody(create, "400");
        assertErrorBody(create, "403");
        assertErrorBody(create, "404");
        assertErrorBody(create, "500");

        Operation read = collection.getGet();
        assertThat(read.getParameters()).extracting(Parameter::getName).contains("q", "page", "size", "sort");
        assertThat(read.getResponses()).containsKeys("200", "400", "403", "404", "500");
        assertErrorBody(read, "400");
        assertErrorBody(read, "403");
        assertErrorBody(read, "404");
        assertErrorBody(read, "500");
    }

    @Test
    void generate_shouldModelPageWrapperOnReadResponse() {
        PathItem collection = generate(true, Long.class).getPaths().get(COLLECTION_PATH);

        Schema<?> body = collection.getGet().getResponses().get("200")
            .getContent().get("application/json").getSchema();
        assertThat(body.getProperties()).containsKeys("content", "page");
    }

    @Test
    void generate_shouldBuildReadOneUpdateDeleteOnItem() {
        PathItem item = generate(true, Long.class).getPaths().get(ITEM_PATH);

        assertThat(item.getGet()).isNotNull();
        assertThat(item.getGet().getParameters()).extracting(Parameter::getName).contains("id");
        assertThat(item.getGet().getResponses()).containsKeys("200", "403", "404", "500");
        assertErrorBody(item.getGet(), "403");
        assertErrorBody(item.getGet(), "404");
        assertErrorBody(item.getGet(), "500");

        Operation update = item.getPatch();
        assertThat(update.getRequestBody().getContent()).containsKey("application/json");
        assertThat(update.getResponses()).containsKeys("200", "204", "400", "403", "404", "409", "500");
        assertErrorBody(update, "400");
        assertErrorBody(update, "403");
        assertErrorBody(update, "404");
        assertErrorBody(update, "409");
        assertErrorBody(update, "500");

        assertThat(item.getDelete().getResponses()).containsKeys("204", "403", "404", "409", "500");
        assertErrorBody(item.getDelete(), "403");
        assertErrorBody(item.getDelete(), "404");
        assertErrorBody(item.getDelete(), "409");
        assertErrorBody(item.getDelete(), "500");
    }

    @Test
    void generate_shouldOmitUnexposedOperationsAndEmptyItemPath() {
        OpenAPI openApi = generate(true, Long.class,
            EnumSet.of(DalOperationType.CREATE, DalOperationType.READ));

        // Only the collection path is documented, carrying just POST and GET.
        assertThat(openApi.getPaths()).containsKey(COLLECTION_PATH);
        assertThat(openApi.getPaths()).doesNotContainKey(ITEM_PATH);

        PathItem collection = openApi.getPaths().get(COLLECTION_PATH);
        assertThat(collection.getPost()).isNotNull();
        assertThat(collection.getGet()).isNotNull();
    }

    @Test
    void generate_shouldOmitCollectionPathWhenOnlyItemOperationsExposed() {
        OpenAPI openApi = generate(true, Long.class, EnumSet.of(DalOperationType.READ_ONE));

        assertThat(openApi.getPaths()).doesNotContainKey(COLLECTION_PATH);
        assertThat(openApi.getPaths()).containsKey(ITEM_PATH);

        PathItem item = openApi.getPaths().get(ITEM_PATH);
        assertThat(item.getGet()).isNotNull();
        assertThat(item.getPatch()).isNull();
        assertThat(item.getDelete()).isNull();
    }

    @Test
    void generate_shouldTypeSimpleIdAsString() {
        PathItem item = generate(true, Long.class).getPaths().get(ITEM_PATH);

        Parameter idParam = idParameter(item);
        assertThat(idParam.getSchema().getType()).isEqualTo("string");
        assertThat(idParam.getDescription()).doesNotContain("Base64");
    }

    @Test
    void generate_shouldDocumentBase64ForComplexId() {
        PathItem item = generate(true, CompositeId.class).getPaths().get(ITEM_PATH);

        assertThat(idParameter(item).getDescription()).contains("Base64");
    }

    @Test
    void generate_shouldAttachDecodableBase64ExampleForComplexId() {
        PathItem item = generate(true, CompositeId.class).getPaths().get(ITEM_PATH);

        Object example = idParameter(item).getSchema().getExample();
        assertThat(example).as("composite id param carries a copy-pasteable example").isNotNull();

        String decoded = new String(
            Base64.getUrlDecoder().decode(example.toString()), StandardCharsets.UTF_8);
        // The example decodes to the id's JSON skeleton, keyed by the composite fields.
        assertThat(decoded).contains("tenant", "code");
    }

    @Test
    void generate_shouldTagPerDalCapitalizedByDefault() {
        PathItem collection = generate(true, Long.class).getPaths().get(COLLECTION_PATH);

        assertThat(collection.getPost().getTags()).containsExactly("Products");
    }

    @Test
    void generate_shouldUseDefaultTagWhenTaggingDisabled() {
        PathItem collection = generate(false, Long.class).getPaths().get(COLLECTION_PATH);

        assertThat(collection.getPost().getTags()).containsExactly("DAL");
    }

    @Test
    void generate_shouldTolerateEmptyDalName() {
        OpenAPI openApi = new OpenAPI().paths(new Paths());
        Schema<Object> problemDetailRef = new Schema<>();
        problemDetailRef.set$ref("#/components/schemas/ProblemDetail");

        generator(true).generate(openApi, "", Product.class, Long.class, problemDetailRef,
            EnumSet.allOf(DalOperationType.class));

        // An empty name capitalizes to itself and still registers under the base path.
        assertThat(openApi.getPaths()).containsKey("/dal/v1/");
        assertThat(openApi.getPaths().get("/dal/v1/").getPost().getTags()).containsExactly("");
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_shouldBuildSkeletonExampleForWideCompositeId() {
        PathItem item = generate(true, WideCompositeId.class).getPaths().get(ITEM_PATH);

        Object example = idParameter(item).getSchema().getExample();
        assertThat(example).as("composite id param carries a copy-pasteable example").isNotNull();

        String decoded = new String(
            Base64.getUrlDecoder().decode(example.toString()), StandardCharsets.UTF_8);
        Map<String, Object> skeleton =
            (Map<String, Object>) JsonMapper.builder().build().readValue(decoded, Map.class);

        // The synthetic this$0 field of the non-static inner id class is skipped.
        assertThat(skeleton)
            .doesNotContainKey("this$0")
            // A non-empty @JsonProperty value renames the key; a bare one falls back to the field name.
            .containsEntry("tenant_id", "string")
            .containsEntry("code", "string")
            // Booleans (primitive and boxed) default to false, Numerics to 0.
            .containsEntry("primitiveFlag", false)
            .containsEntry("boxedFlag", false)
            .containsEntry("i", 0)
            .containsEntry("l", 0)
            .containsEntry("d", 0)
            .containsEntry("f", 0)
            .containsEntry("s", 0)
            .containsEntry("b", 0);
    }

    @Test
    void generate_shouldBuildEmptySkeletonForInterfaceId() {
        PathItem item = generate(true, MarkerId.class).getPaths().get(ITEM_PATH);

        Parameter idParam = idParameter(item);
        assertThat(idParam.getDescription()).contains("Base64");

        // The interface constant is static and excluded, so the skeleton is empty.
        String decoded = new String(
            Base64.getUrlDecoder().decode(idParam.getSchema().getExample().toString()),
            StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("{}");
    }

    /**
     * Asserts the given response carries an {@code application/problem+json} body referencing the shared
     * {@code ProblemDetail} schema.
     */
    private static void assertErrorBody(Operation operation, String status) {
        Schema<?> schema = operation.getResponses().get(status)
            .getContent().get("application/problem+json").getSchema();
        assertThat(schema.get$ref()).isEqualTo("#/components/schemas/ProblemDetail");
    }

    private static Parameter idParameter(PathItem item) {
        return item.getGet().getParameters().stream()
            .filter(parameter -> "id".equals(parameter.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No 'id' parameter on item operation"));
    }

    /**
     * A wide composite id covering every skeleton placeholder shape. Declared as a non-static inner
     * class on purpose: its compiler-generated {@code this$0} field is synthetic and must be skipped.
     * The outer reference below is deliberate — without it the compiler omits {@code this$0} entirely.
     */
    @SuppressWarnings("unused")
    class WideCompositeId {

        private Object outer() {
            return DalPathsGeneratorTest.this;
        }

        @JsonProperty("tenant_id")
        private String tenant;

        @JsonProperty
        private String code;

        private boolean primitiveFlag;

        private Boolean boxedFlag;

        private int i;

        private long l;

        private double d;

        private float f;

        private short s;

        private byte b;
    }

    /**
     * An interface id type: complex (so Base64-documented) but with only a constant, which is static
     * and excluded from the skeleton.
     */
    @SuppressWarnings("unused")
    interface MarkerId {
        String KIND = "k";
    }
}
