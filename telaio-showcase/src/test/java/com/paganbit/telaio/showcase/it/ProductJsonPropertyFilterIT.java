package com.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that filter queries resolve {@code @JsonProperty}-renamed fields through the full
 * REST stack. {@code Product.costPrice} is exposed as {@code cost_price} and {@code internalSku} as
 * {@code internal_sku}; before the {@code JsonAwareFilterSpecificationConverter} fix, filtering on those
 * wire names failed because Turkraft resolved them straight against the JPA metamodel.
 */
class ProductJsonPropertyFilterIT extends AbstractShowcaseIT {

    private static final String DAL = "products";

    private int skuCounter = 0;

    private Map<String, Object> productPayload(String name, String price, String cost, String category) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("description", "Created by ProductJsonPropertyFilterIT.");
        payload.put("price", new BigDecimal(price));
        payload.put("cost_price", new BigDecimal(cost));
        payload.put("sku", "IT-JPF-" + System.nanoTime() + "-" + (skuCounter++));
        payload.put("internal_sku", "IT-JPF-INT-" + System.nanoTime() + "-" + skuCounter);
        payload.put("category", category);
        payload.put("available", true);
        return payload;
    }

    private JsonNode createAsDeveloper(Map<String, Object> payload) {
        ResponseEntity<String> created = create(DEVELOPER, DAL, body(payload));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return tree(created);
    }

    /**
     * Passes the filter raw — {@code TestRestTemplate} encodes the URI template exactly once.
     */
    private String filterQuery(String filter) {
        return "q=" + filter;
    }

    @Test
    void filtersOnRenamedNumericFieldByItsJsonName() {
        String category = "it-jpf-num-" + System.nanoTime();
        createAsDeveloper(productPayload("Cheap", "10.00", "5.00", category));
        createAsDeveloper(productPayload("Mid", "80.00", "50.00", category));
        createAsDeveloper(productPayload("Premium", "200.00", "150.00", category));

        ResponseEntity<String> response = list(DEVELOPER, DAL,
            filterQuery("category:'" + category + "' and cost_price>100"));

        assertThat(response.getStatusCode())
            .as("filtering on the JSON wire name must no longer fail").isEqualTo(HttpStatus.OK);
        JsonNode page = tree(response);
        assertThat(page.get("page").get("totalElements").asLong()).isEqualTo(1);
        JsonNode only = page.get("content").get(0);
        assertThat(only.get("name").asString()).isEqualTo("Premium");
        assertThat(new BigDecimal(only.get("cost_price").asString())).isEqualByComparingTo("150.00");
    }

    @Test
    void stillFiltersOnTheUnderlyingJavaPropertyName() {
        String category = "it-jpf-java-" + System.nanoTime();
        createAsDeveloper(productPayload("Cheap", "10.00", "5.00", category));
        createAsDeveloper(productPayload("Premium", "200.00", "150.00", category));

        ResponseEntity<String> response = list(DEVELOPER, DAL,
            filterQuery("category:'" + category + "' and costPrice>100"));

        assertThat(response.getStatusCode())
            .as("the Java attribute name must keep working (additive fix)").isEqualTo(HttpStatus.OK);
        JsonNode page = tree(response);
        assertThat(page.get("page").get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("name").asString()).isEqualTo("Premium");
    }

    @Test
    void filtersOnRenamedStringFieldByItsJsonName() {
        JsonNode created = createAsDeveloper(productPayload("Tracked", "30.00", "10.00", "it-jpf-sku"));
        String internalSku = created.get("internal_sku").asString();

        ResponseEntity<String> response = list(DEVELOPER, DAL,
            filterQuery("internal_sku:'" + internalSku + "'"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = tree(response);
        assertThat(page.get("page").get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("internal_sku").asString()).isEqualTo(internalSku);
    }
}
