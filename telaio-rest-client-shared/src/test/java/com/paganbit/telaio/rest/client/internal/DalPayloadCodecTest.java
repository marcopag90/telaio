package com.paganbit.telaio.rest.client.internal;

import com.paganbit.telaio.rest.client.DalPage;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DalPayloadCodecTest {

    /**
     * Literal snapshot of the server's {@code PagedModel} wire shape.
     */
    private static final String PAGED_MODEL = """
        {
          "content": [
            {"id": 1, "name": "alpha", "price": 10.5},
            {"id": 2, "name": "beta", "price": 20.0}
          ],
          "page": {"size": 20, "number": 0, "totalElements": 2, "totalPages": 1}
        }""";

    record Product(@Nullable Long id, @Nullable String name, @Nullable Double price) {
    }

    record Patch(@Nullable String name, @Nullable Double price, @Nullable Nested nested) {
        record Nested(@Nullable String code, @Nullable String label) {
        }
    }

    private final JsonMapper baseMapper = JsonMapper.builder().build();
    private final DalPayloadCodec codec = new DalPayloadCodec(baseMapper);

    @Test
    void mapPayloadKeepsExplicitNullsOnTheWire() {
        Map<String, @Nullable Object> input = new HashMap<>();
        input.put("name", "alpha");
        input.put("price", null);
        Map<String, @Nullable Object> nested = new HashMap<>();
        nested.put("label", null);
        input.put("nested", nested);

        JsonNode node = baseMapper.readTree(codec.toWireJson(input));

        assertThat(node.path("name").asString()).isEqualTo("alpha");
        assertThat(node.has("price")).isTrue();
        assertThat(node.path("price").isNull()).isTrue();
        assertThat(node.path("nested").path("label").isNull()).isTrue();
    }

    @Test
    void mapNullsSurviveHostNonNullJacksonConfiguration() {
        // Regression: an app-wide NON_NULL inclusion must not silently drop the merge-patch
        // explicit null-set — the codec writes the wire bytes with its own pinned mapper.
        JsonMapper nonNullMapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL))
            .build();
        DalPayloadCodec nonNullCodec = new DalPayloadCodec(nonNullMapper);

        Map<String, @Nullable Object> input = new HashMap<>();
        input.put("price", null);

        String json = nonNullCodec.toWireJson(input);

        assertThat(json).contains("\"price\":null");
    }

    @Test
    void dtoPayloadDropsNullFieldsAtEveryLevel() {
        Patch patch = new Patch("alpha", null, new Patch.Nested("C1", null));

        JsonNode node = baseMapper.readTree(codec.toWireJson(patch));

        assertThat(node.path("name").asString()).isEqualTo("alpha");
        assertThat(node.has("price")).isFalse();
        assertThat(node.path("nested").path("code").asString()).isEqualTo("C1");
        assertThat(node.path("nested").has("label")).isFalse();
    }

    @Test
    void pagedModelDeserializesIntoDalPage() {
        JsonNode root = baseMapper.readTree(PAGED_MODEL);

        DalPage<Product> page = codec.toPage(root, Product.class);

        assertThat(page.content()).containsExactly(
            new Product(1L, "alpha", 10.5),
            new Product(2L, "beta", 20.0));
        assertThat(page.page()).isEqualTo(new DalPage.Metadata(20, 0, 2, 1));
        assertThat(page.hasContent()).isTrue();
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void entityDeserializationToleratesMissingAndUnknownMembers() {
        // RBAC may strip fields (price absent); newer servers may add fields (extra unknown).
        JsonNode node = baseMapper.readTree("""
            {"id": 7, "name": "gamma", "extra": {"future": true}}""");

        Product product = codec.toEntity(node, Product.class);

        assertThat(product).isEqualTo(new Product(7L, "gamma", null));
    }

    @Test
    void unknownTopLevelPageMembersAreIgnored() {
        JsonNode root = baseMapper.readTree("""
            {"content": [], "page": {"size": 20, "number": 3, "totalElements": 0, "totalPages": 0},
             "futureExtension": 42}""");

        DalPage<Product> page = codec.toPage(root, Product.class);

        assertThat(page.content()).isEmpty();
        assertThat(page.page().number()).isEqualTo(3);
    }

    @Test
    void nonPageShapedResponseIsRejectedInsteadOfFabricatingMetadata() {
        JsonNode root = baseMapper.readTree("""
            {"something": "else"}""");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codec.toPage(root, Product.class))
            .isInstanceOf(com.paganbit.telaio.rest.client.exception.DalClientMalformedResponseException.class)
            .hasMessageContaining("page");
    }

    @Test
    void nullResponseBodyIsRejectedAsMalformedPage() {
        Assertions.assertThatThrownBy(() -> codec.toPage(null, Product.class))
            .isInstanceOf(com.paganbit.telaio.rest.client.exception.DalClientMalformedResponseException.class);
    }

    @Test
    void pageWithContentArrayButNonObjectMetadataIsRejected() {
        JsonNode root = baseMapper.readTree("""
            {"content": [], "page": "not-an-object"}""");

        Assertions.assertThatThrownBy(() -> codec.toPage(root, Product.class))
            .isInstanceOf(com.paganbit.telaio.rest.client.exception.DalClientMalformedResponseException.class)
            .hasMessageContaining("page");
    }

    @Test
    void mapPayloadPreservesNullsInsideListsOfMaps() {
        Map<String, @Nullable Object> firstItem = new HashMap<>();
        firstItem.put("code", "C1");
        firstItem.put("label", null);
        Map<String, Object> input = new HashMap<>();
        input.put("items", java.util.Arrays.asList(firstItem, null, "scalar"));

        JsonNode node = baseMapper.readTree(codec.toWireJson(input));

        JsonNode items = node.path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.get(0).path("code").asString()).isEqualTo("C1");
        assertThat(items.get(0).has("label")).isTrue();
        assertThat(items.get(0).path("label").isNull()).isTrue();
        assertThat(items.get(1).isNull()).isTrue();
        assertThat(items.get(2).asString()).isEqualTo("scalar");
    }

    @Test
    void dtoPayloadDropsNullMembersInsideListElements() {
        record Line(@Nullable String sku, @Nullable String note) {
        }
        record Order(@Nullable String ref, List<Line> lines) {
        }
        Order order = new Order("R1", List.of(new Line("S1", null)));

        JsonNode node = baseMapper.readTree(codec.toWireJson(order));

        JsonNode lines = node.path("lines");
        assertThat(lines.isArray()).isTrue();
        assertThat(lines.get(0).path("sku").asString()).isEqualTo("S1");
        assertThat(lines.get(0).has("note")).isFalse();
    }

    @Test
    void nullInputIsRejected() {
        Assertions.assertThatNullPointerException()
            .isThrownBy(() -> codec.toWireJson(null));
    }

    @Test
    void nullMapperIsRejected() {
        Assertions.assertThatNullPointerException()
            .isThrownBy(() -> new DalPayloadCodec(null));
    }

    @Test
    void mapperExposesTheLenientPinnedMapper() {
        assertThat(codec.mapper()).isNotNull().isNotSameAs(baseMapper);
    }
}
