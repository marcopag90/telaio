package com.paganbit.telaio.web;

import com.paganbit.telaio.core.adapter.DalOperation;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DalRestOperationMappingTest {

    @Test
    void pathTemplates_matchTheWireContract() {
        assertThat(DalRestOperationMapping.COLLECTION_PATH).isEqualTo("{dalName}");
        assertThat(DalRestOperationMapping.ITEM_PATH).isEqualTo("{dalName}/{id}");
    }

    @Test
    void uriGroups_partitionTheFiveOperations() {
        assertThat(DalRestOperationMapping.COLLECTION_OPERATIONS)
            .containsExactlyInAnyOrder(DalOperationType.CREATE, DalOperationType.READ);
        assertThat(DalRestOperationMapping.ITEM_OPERATIONS)
            .containsExactlyInAnyOrder(DalOperationType.READ_ONE, DalOperationType.UPDATE, DalOperationType.DELETE);
        assertThat(DalRestOperationMapping.uriGroup(DalOperationType.READ))
            .isSameAs(DalRestOperationMapping.COLLECTION_OPERATIONS);
        assertThat(DalRestOperationMapping.uriGroup(DalOperationType.DELETE))
            .isSameAs(DalRestOperationMapping.ITEM_OPERATIONS);
    }

    @Test
    void httpMethods() {
        assertThat(DalRestOperationMapping.httpMethod(DalOperationType.CREATE)).isEqualTo(HttpMethod.POST);
        assertThat(DalRestOperationMapping.httpMethod(DalOperationType.READ)).isEqualTo(HttpMethod.GET);
        assertThat(DalRestOperationMapping.httpMethod(DalOperationType.READ_ONE)).isEqualTo(HttpMethod.GET);
        assertThat(DalRestOperationMapping.httpMethod(DalOperationType.UPDATE)).isEqualTo(HttpMethod.PATCH);
        assertThat(DalRestOperationMapping.httpMethod(DalOperationType.DELETE)).isEqualTo(HttpMethod.DELETE);
    }

    @Test
    void allowedMethods_listTheExposedSiblingsOnTheSameUri() {
        Set<DalOperationType> readOnly = EnumSet.of(DalOperationType.READ, DalOperationType.READ_ONE);

        assertThat(DalRestOperationMapping.allowedMethods(readOnly, DalOperationType.DELETE))
            .containsExactly(HttpMethod.GET);
        assertThat(DalRestOperationMapping.allowedMethods(readOnly, DalOperationType.CREATE))
            .containsExactly(HttpMethod.GET);
    }

    @Test
    void allowedMethods_keepOperationOrderAndDropDuplicates() {
        Set<DalOperationType> all = EnumSet.allOf(DalOperationType.class);

        assertThat(DalRestOperationMapping.allowedMethods(all, DalOperationType.DELETE))
            .containsExactly(HttpMethod.GET, HttpMethod.PATCH, HttpMethod.DELETE);
    }

    @Test
    void allowedMethods_areEmptyWhenTheUriServesNothing() {
        Set<DalOperationType> appendOnly = EnumSet.of(DalOperationType.CREATE, DalOperationType.READ);

        assertThat(DalRestOperationMapping.allowedMethods(appendOnly, DalOperationType.READ_ONE)).isEmpty();
        assertThat(DalRestOperationMapping.allowedMethods(Set.of(), DalOperationType.READ)).isEmpty();
    }

    /**
     * The contract interface is annotation-driven and cannot consume the mapping at run time, so this
     * proves the two agree: for every operation, the {@code @*Mapping} on the {@link DalRestApiV1} method
     * carrying that {@link DalOperation} uses the mapping's HTTP method and URI template.
     */
    @ParameterizedTest
    @EnumSource(DalOperationType.class)
    void contractAnnotations_agreeWithTheMapping(DalOperationType operation) throws NoSuchMethodException {
        Method adapterMethod = adapterMethod(operation);
        Method contractMethod = contractMethod(adapterMethod.getName());
        RequestMapping mapping =
            AnnotatedElementUtils.findMergedAnnotation(contractMethod, RequestMapping.class);
        assertThat(mapping).isNotNull();

        assertThat(mapping.method()).hasSize(1);
        assertThat(mapping.method()[0].asHttpMethod())
            .isEqualTo(DalRestOperationMapping.httpMethod(operation));
        String expectedPath = DalRestOperationMapping.COLLECTION_OPERATIONS.contains(operation)
            ? DalRestOperationMapping.COLLECTION_PATH
            : DalRestOperationMapping.ITEM_PATH;
        assertThat(mapping.value()).containsExactly(expectedPath);
    }

    private static Method adapterMethod(DalOperationType operation) {
        for (Method method : DalOperationAdapter.class.getMethods()) {
            DalOperation annotation = method.getAnnotation(DalOperation.class);
            if (annotation != null && annotation.value() == operation) {
                return method;
            }
        }
        throw new IllegalStateException("No adapter method for " + operation);
    }

    private static Method contractMethod(String name) {
        for (Method method : DalRestApiV1.class.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalStateException("No contract method named " + name);
    }
}
