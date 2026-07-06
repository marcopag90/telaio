package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.showcase.dal.product.ProductPriceHistory;
import com.paganbit.telaio.showcase.dal.product.ProductPriceHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use case — the most complete DAL: custom per-operation authorization ({@code ProductAuthAdapter}),
 * property-based field RBAC ({@code ProductRbacAdapter}), a derived persistent field
 * ({@code marginPercentage}), a transient field ({@code profit}), and a transactional side effect
 * (a {@code ProductPriceHistory} row written from the lifecycle hooks). All exercised end-to-end.
 */
class ProductRbacHooksIT extends AbstractShowcaseIT {

    private static final String DAL = "products";

    @Autowired
    private ProductPriceHistoryRepository priceHistoryRepository;

    private int skuCounter = 0;

    /**
     * Builds a full, valid product payload (only DEVELOPER may write the sensitive cost/internal fields).
     */
    private Map<String, Object> productPayload(String name, String price, String cost, String category) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("description", "Created by ProductRbacHooksIT.");
        payload.put("price", new BigDecimal(price));
        payload.put("cost_price", new BigDecimal(cost));
        payload.put("sku", "IT-PROD-" + System.nanoTime() + "-" + (skuCounter++));
        payload.put("internal_sku", "IT-INT-" + System.nanoTime() + "-" + skuCounter);
        payload.put("category", category);
        payload.put("available", true);
        return payload;
    }

    private String createAsDeveloper(Map<String, Object> payload) {
        ResponseEntity<String> created = create(DEVELOPER, DAL, body(payload));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return tree(created).get("id").asString();
    }

    private String filterQuery(String filter) {
        // Pass the filter raw: TestRestTemplate treats the URL as a URI template and encodes it once.
        // Pre-encoding here would double-encode ('%' would survive to the parser as a literal token).
        return "q=" + filter;
    }

    // --- Authorization (custom per-operation) -------------------------------------------------

    @Test
    void userCanReadButCannotWrite() {
        assertThat(list(USER, DAL, "size=5").getStatusCode())
            .as("read is open to everyone").isEqualTo(HttpStatus.OK);

        ResponseEntity<String> create = create(USER, DAL, body(productPayload("Nope", "10.00", "5.00", "it-deny")));
        assertThat(create.getStatusCode()).as("USER create denied").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch(USER, DAL, 1L, body(Map.of("price", new BigDecimal("1.00")))).getStatusCode())
            .as("USER update denied").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete(USER, DAL, 1L).getStatusCode())
            .as("USER delete denied").isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- Field RBAC on output -----------------------------------------------------------------

    @Test
    void sensitiveFieldsAreHiddenFromUserAndAdmin() {
        String id = createAsDeveloper(productPayload("Sensitive", "200.00", "150.00", "it-sensitive"));

        for (String user : new String[]{USER, ADMIN}) {
            JsonNode product = tree(getOne(user, DAL, id));
            // Visible to everyone
            assertThat(product.has("name")).isTrue();
            assertThat(product.has("price")).isTrue();
            assertThat(product.has("sku")).isTrue();
            // Hidden from USER and ADMIN
            assertThat(product.has("cost_price")).as("cost_price hidden from %s", user).isFalse();
            assertThat(product.has("internal_sku")).as("internal_sku hidden from %s", user).isFalse();
            assertThat(product.has("marginPercentage")).as("margin hidden from %s", user).isFalse();
            assertThat(product.has("profit")).as("profit hidden from %s", user).isFalse();
        }
    }

    @Test
    void developerSeesSensitiveFieldsAndTransientProfit() {
        String id = createAsDeveloper(productPayload("Visible", "200.00", "150.00", "it-visible"));

        JsonNode product = tree(getOne(DEVELOPER, DAL, id));
        assertThat(product.has("cost_price")).isTrue();
        assertThat(product.has("internal_sku")).isTrue();
        assertThat(product.has("marginPercentage")).isTrue();
        assertThat(new BigDecimal(product.get("profit").asString()))
            .as("transient profit = price - cost").isEqualByComparingTo("50.00");
    }

    // --- Derived field + transient field + transactional side-effect --------------------------

    @Test
    void createDerivesMarginComputesProfitAndWritesInitialHistory() {
        ResponseEntity<String> created = create(DEVELOPER, DAL,
            body(productPayload("Margin", "200.00", "150.00", "it-margin")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode body = tree(created);
        long productId = Long.parseLong(body.get("id").asString());
        assertThat(new BigDecimal(body.get("marginPercentage").asString()))
            .as("margin = (price - cost) / price * 100").isEqualByComparingTo("25.00");
        assertThat(new BigDecimal(body.get("profit").asString())).isEqualByComparingTo("50.00");

        assertThat(priceHistoryRepository.findAll())
            .as("create wrote an INITIAL price-history row in the same transaction")
            .anySatisfy(history -> assertHistory(history, productId, "200.00", "INITIAL"));
    }

    @Test
    void clientSuppliedMarginPercentageIsIgnoredAndRecomputed() {
        Map<String, Object> payload = productPayload("ForgedMargin", "200.00", "150.00", "it-forged");
        payload.put("marginPercentage", new BigDecimal("99.00")); // not writable -> dropped, then recomputed

        JsonNode body = tree(create(DEVELOPER, DAL, body(payload)));
        assertThat(new BigDecimal(body.get("marginPercentage").asString()))
            .as("server recomputes margin, ignoring the client value").isEqualByComparingTo("25.00");
    }

    @Test
    void adminCostPriceWriteIsDroppedWhilePriceWriteApplies() {
        // DEVELOPER seeds a product: price 100, cost 60 -> margin 40.00.
        String id = createAsDeveloper(productPayload("AdminPatch", "100.00", "60.00", "it-adminpatch"));

        // ADMIN raises price (allowed) and tries to slash cost (not writable -> dropped).
        ResponseEntity<String> patched = patch(ADMIN, DAL, id, body(Map.of(
            "price", new BigDecimal("120.00"),
            "cost_price", new BigDecimal("10.00")
        )));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(patched).has("cost_price")).as("ADMIN cannot see cost_price").isFalse();

        // DEVELOPER confirms: price changed, cost untouched, margin recomputed from the untouched cost.
        JsonNode developerView = tree(getOne(DEVELOPER, DAL, id));
        assertThat(new BigDecimal(developerView.get("price").asString())).isEqualByComparingTo("120.00");
        assertThat(new BigDecimal(developerView.get("cost_price").asString()))
            .as("ADMIN's cost_price write was dropped").isEqualByComparingTo("60.00");
        assertThat(new BigDecimal(developerView.get("marginPercentage").asString()))
            .as("margin = (120 - 60) / 120 * 100").isEqualByComparingTo("50.00");
    }

    @Test
    void developerCostPriceWriteAppliesAndWritesUpdateHistory() {
        String id = createAsDeveloper(productPayload("DevPatch", "100.00", "60.00", "it-devpatch"));
        long productId = Long.parseLong(id);

        ResponseEntity<String> patched = patch(DEVELOPER, DAL, id, body(Map.of(
            "cost_price", new BigDecimal("40.00")
        )));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = tree(patched);
        assertThat(new BigDecimal(body.get("cost_price").asString()))
            .as("DEVELOPER may write cost_price").isEqualByComparingTo("40.00");
        assertThat(new BigDecimal(body.get("marginPercentage").asString()))
            .as("margin = (100 - 40) / 100 * 100").isEqualByComparingTo("60.00");

        assertThat(priceHistoryRepository.findAll())
            .as("update wrote an UPDATE price-history row")
            .anySatisfy(history -> assertHistory(history, productId, "100.00", "UPDATE"));
    }

    // --- Filtering, pagination, sorting -------------------------------------------------------

    @Test
    void filteringPaginationAndSortingWork() {
        String category = "it-paging-" + System.nanoTime();
        createAsDeveloper(productPayload("Cheap", "10.00", "5.00", category));
        createAsDeveloper(productPayload("Mid", "20.00", "5.00", category));
        createAsDeveloper(productPayload("Pricey", "30.00", "5.00", category));

        // Filter to just this category, sorted by price descending, first page of 2.
        JsonNode page = tree(list(DEVELOPER, DAL,
            filterQuery("category:'" + category + "'") + "&sort=price,desc&size=2&page=0"));

        // Spring Data pages serialize VIA_DTO: pagination metadata is nested under "page".
        JsonNode pageInfo = page.get("page");
        assertThat(pageInfo.get("totalElements").asLong()).as("filter matches exactly the 3 created rows").isEqualTo(3);
        assertThat(pageInfo.get("totalPages").asLong()).isEqualTo(2);
        JsonNode content = page.get("content");
        assertThat(content.size()).as("page size honoured").isEqualTo(2);
        BigDecimal first = new BigDecimal(content.get(0).get("price").asString());
        BigDecimal second = new BigDecimal(content.get(1).get("price").asString());
        assertThat(first).as("descending price sort").isEqualByComparingTo("30.00");
        assertThat(second).isEqualByComparingTo("20.00");
    }

    private void assertHistory(ProductPriceHistory history, long productId, String price, String reason) {
        assertThat(history.getProductId()).isEqualTo(productId);
        assertThat(history.getPrice()).isEqualByComparingTo(price);
        assertThat(history.getReason()).isEqualTo(reason);
    }
}
