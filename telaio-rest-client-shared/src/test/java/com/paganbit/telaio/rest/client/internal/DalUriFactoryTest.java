package com.paganbit.telaio.rest.client.internal;

import com.paganbit.telaio.rest.client.DalPageRequest;
import com.paganbit.telaio.rest.client.DalSort;
import com.paganbit.telaio.rest.client.internal.DalUriFactory.DalRequestUri;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DalUriFactoryTest {

    private final DalUriFactory factory = new DalUriFactory();

    /** Expands like the HTTP client does: default UriBuilderFactory, strict template encoding. */
    private static URI expand(DalRequestUri uri, String baseUrl) {
        return new DefaultUriBuilderFactory(baseUrl).expand(uri.template(), uri.variables());
    }

    @Test
    void collectionTemplate() {
        DalRequestUri uri = factory.collection("products");

        assertThat(uri.template()).isEqualTo("/dal/v1/{dalName}");
        assertThat(expand(uri, "http://localhost:8080"))
            .hasToString("http://localhost:8080/dal/v1/products");
    }

    @Test
    void baseUrlContextPathIsPreservedByExpansion() {
        assertThat(expand(factory.collection("products"), "http://localhost:8080/app"))
            .hasToString("http://localhost:8080/app/dal/v1/products");
    }

    @Test
    void collectionTemplateWithFilterPagingAndSort() {
        DalRequestUri uri = factory.collection("products", "name:'telaio'",
            DalPageRequest.of(2, 50).withSort(DalSort.asc("name"), DalSort.desc("price")));

        assertThat(uri.template()).isEqualTo(
            "/dal/v1/{dalName}?q={filter}&page={page}&size={size}"
                + "&sort={sortProperty0},{sortDirection0}&sort={sortProperty1},{sortDirection1}");
        // The filter value is strictly encoded; the server decodes it before parsing.
        assertThat(expand(uri, "http://localhost:8080")).hasToString(
            "http://localhost:8080/dal/v1/products"
                + "?q=name%3A%27telaio%27&page=2&size=50&sort=name,asc&sort=price,desc");
    }

    @Test
    void unpagedRequestSendsNoPagingParameters() {
        DalRequestUri uri = factory.collection("products", null, DalPageRequest.unpaged());

        assertThat(uri.template()).isEqualTo("/dal/v1/{dalName}");
        assertThat(uri.variables()).containsOnlyKeys("dalName");
    }

    @Test
    void blankFilterIsOmitted() {
        DalRequestUri uri = factory.collection("products", "  ", DalPageRequest.unpaged());

        assertThat(uri.template()).isEqualTo("/dal/v1/{dalName}");
    }

    @Test
    void filterWithReservedCharactersIsStrictlyEncoded() {
        DalRequestUri uri = factory.collection("products", "name:'a b&c'", DalPageRequest.unpaged());

        URI expanded = expand(uri, "http://localhost:8080");
        assertThat(expanded.getRawQuery()).contains("a%20b%26c");
    }

    @Test
    void entityTemplate() {
        DalRequestUri uri = factory.entity("products", "42");

        assertThat(uri.template()).isEqualTo("/dal/v1/{dalName}/{id}");
        assertThat(expand(uri, "http://localhost:8080"))
            .hasToString("http://localhost:8080/dal/v1/products/42");
    }

    @Test
    void entityTemplateEncodesUnsafeSegments() {
        URI expanded = expand(factory.entity("products", "a b/c"), "http://localhost:8080");

        assertThat(expanded.getRawPath()).isEqualTo("/dal/v1/products/a%20b%2Fc");
    }

    @Test
    void variablesAreImmutable() {
        var variables = factory.collection("products").variables();

        Assertions.assertThatThrownBy(() -> variables.put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
