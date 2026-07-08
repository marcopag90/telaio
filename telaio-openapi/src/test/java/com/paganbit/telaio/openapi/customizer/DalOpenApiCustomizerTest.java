package com.paganbit.telaio.openapi.customizer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.openapi.generator.DalPathsGenerator;
import com.paganbit.telaio.openapi.generator.FilterParameterDescriber;
import com.paganbit.telaio.openapi.introspection.DalEntitySchemaResolver;
import com.turkraft.springfilter.parser.node.FilterNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link DalOpenApiCustomizer} replaces the generic templated operations with concrete,
 * per-DAL operations and registers the corresponding schemas.
 */
class DalOpenApiCustomizerTest {

    private static final String GENERIC_COLLECTION = "/dal/v1/{dalName}";
    private static final String GENERIC_ITEM = "/dal/v1/{dalName}/{id}";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private DalOpenApiCustomizer customizer(DalManager dalManager) {
        DalEntitySchemaResolver schemaResolver = new DalEntitySchemaResolver();
        FilterParameterDescriber describer =
            new FilterParameterDescriber(new JsonPropertyPathResolver(objectMapper), true);
        DalPathsGenerator generator = new DalPathsGenerator(schemaResolver, describer, objectMapper, true);
        return new DalOpenApiCustomizer(dalManager, generator, schemaResolver);
    }

    @Test
    void shouldSynthesizePerDalPaths_andRemoveGenericOnes() {
        DalManager dalManager = mock(DalManager.class);
        when(dalManager.getAllDefinitions())
            .thenReturn(List.of(new DalDefinitionEntry("products", TestProductDal.class)));
        doReturn(new TestProductDal()).when(dalManager).getServiceByName("products");

        OpenAPI openApi = new OpenAPI().paths(new Paths());
        openApi.getPaths().addPathItem(GENERIC_COLLECTION, new PathItem().post(new Operation()));
        openApi.getPaths().addPathItem(GENERIC_ITEM, new PathItem().get(new Operation()));

        customizer(dalManager).customise(openApi);

        // Generic templated operations are gone, concrete per-DAL ones are present.
        assertThat(openApi.getPaths()).doesNotContainKeys(GENERIC_COLLECTION, GENERIC_ITEM);
        assertThat(openApi.getPaths()).containsKeys("/dal/v1/products", "/dal/v1/products/{id}");

        PathItem collection = openApi.getPaths().get("/dal/v1/products");
        assertThat(collection.getPost()).isNotNull();
        assertThat(collection.getGet()).isNotNull();
        assertThat(collection.getGet().getParameters())
            .extracting(Parameter::getName)
            .contains("q", "page", "size", "sort");

        PathItem item = openApi.getPaths().get("/dal/v1/products/{id}");
        assertThat(item.getGet()).isNotNull();
        assertThat(item.getPatch()).isNotNull();
        assertThat(item.getDelete()).isNotNull();
        assertThat(item.getGet().getParameters()).extracting(Parameter::getName).contains("id");

        // Operations are tagged per DAL, with a capitalized display title.
        assertThat(collection.getPost().getTags()).contains("Products");

        // Entity and the shared ProblemDetail error-body schema are registered (Jackson @JsonProperty honored).
        assertThat(openApi.getComponents().getSchemas().keySet())
            .anyMatch(name -> name.contains("Product"));
        assertThat(openApi.getComponents().getSchemas().keySet())
            .anyMatch(name -> name.contains("ProblemDetail"));
    }

    @Test
    void shouldNotDocumentInternalDal() {
        DalManager dalManager = mock(DalManager.class);
        when(dalManager.getAllDefinitions())
            .thenReturn(List.of(new DalDefinitionEntry("products", TestProductDal.class, true)));

        OpenAPI openApi = new OpenAPI().paths(new Paths());
        openApi.getPaths().addPathItem(GENERIC_COLLECTION, new PathItem().post(new Operation()));
        openApi.getPaths().addPathItem(GENERIC_ITEM, new PathItem().get(new Operation()));

        customizer(dalManager).customise(openApi);

        // Generic templated paths are still removed, but no concrete per-DAL paths are synthesized.
        assertThat(openApi.getPaths()).doesNotContainKeys(GENERIC_COLLECTION, GENERIC_ITEM);
        assertThat(openApi.getPaths()).doesNotContainKeys("/dal/v1/products", "/dal/v1/products/{id}");
        assertThat(openApi.getPaths()).isEmpty();
        // The DAL is never resolved, since it is skipped before service lookup.
        verify(dalManager, never()).getServiceByName("products");
    }

    @Test
    void shouldDocumentOnlyExposedOperationsForPartialDal() {
        DalManager dalManager = mock(DalManager.class);
        when(dalManager.getAllDefinitions())
            .thenReturn(List.of(new DalDefinitionEntry("products", TestProductDal.class, false,
                EnumSet.of(DalOperationType.CREATE, DalOperationType.READ))));
        doReturn(new TestProductDal()).when(dalManager).getServiceByName("products");

        OpenAPI openApi = new OpenAPI().paths(new Paths());
        openApi.getPaths().addPathItem(GENERIC_COLLECTION, new PathItem().post(new Operation()));
        openApi.getPaths().addPathItem(GENERIC_ITEM, new PathItem().get(new Operation()));

        customizer(dalManager).customise(openApi);

        // Only the collection path is synthesized; the item path (read-one/update/delete) is absent.
        assertThat(openApi.getPaths()).containsKey("/dal/v1/products");
        assertThat(openApi.getPaths()).doesNotContainKey("/dal/v1/products/{id}");

        PathItem collection = openApi.getPaths().get("/dal/v1/products");
        assertThat(collection.getPost()).isNotNull();
        assertThat(collection.getGet()).isNotNull();
    }

    @Test
    void shouldTolerateEmptyRegistry() {
        DalManager dalManager = mock(DalManager.class);
        when(dalManager.getAllDefinitions()).thenReturn(List.of());

        OpenAPI openApi = new OpenAPI().paths(new Paths());
        openApi.getPaths().addPathItem(GENERIC_COLLECTION, new PathItem().post(new Operation()));

        customizer(dalManager).customise(openApi);

        assertThat(openApi.getPaths()).doesNotContainKey(GENERIC_COLLECTION);
        assertThat(openApi.getPaths()).isEmpty();
    }

    @Test
    void shouldPruneOrphanControllerTag() {
        DalManager dalManager = mock(DalManager.class);
        when(dalManager.getAllDefinitions())
            .thenReturn(List.of(new DalDefinitionEntry("products", TestProductDal.class)));
        doReturn(new TestProductDal()).when(dalManager).getServiceByName("products");

        OpenAPI openApi = new OpenAPI().paths(new Paths());
        openApi.getPaths().addPathItem(GENERIC_COLLECTION, new PathItem().post(new Operation()));
        // The controller-level @Tag springdoc would add; it becomes orphaned once generic ops are removed.
        openApi.setTags(new ArrayList<>(List.of(new Tag().name("DAL API v1"))));

        customizer(dalManager).customise(openApi);

        assertThat(openApi.getTags()).isNullOrEmpty();
    }

    /**
     * Minimal stub DAL exposing the entity/id types the customizer introspects.
     */
    static class TestProductDal implements Dal<Product, Long> {

        @Override
        public Product create(Map<String, Object> properties) {
            return null;
        }

        @Override
        public Page<Product> read(@Nullable FilterNode filter, Pageable pageable) {
            return Page.empty();
        }

        @Override
        public Optional<Product> readOne(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<Product> update(Long id, Map<String, Object> properties) {
            return Optional.empty();
        }

        @Override
        public void delete(Long id) {
            // no-op
        }

        @Override
        public Class<Product> getEntityClass() {
            return Product.class;
        }

        @Override
        public Class<Long> getIdClass() {
            return Long.class;
        }
    }

    /**
     * Sample entity with a renamed property and a validation constraint.
     */
    @SuppressWarnings("unused")
    @Getter
    @Setter
    static class Product {

        private Long id;

        @NotBlank
        private String name;

        private BigDecimal price;

        @JsonProperty("cost_price")
        private BigDecimal costPrice;
    }
}
