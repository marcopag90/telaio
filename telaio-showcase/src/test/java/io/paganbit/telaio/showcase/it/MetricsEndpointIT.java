package io.paganbit.telaio.showcase.it;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting — usage and performance metrics. Metrics are ON by default for every DAL, so exercising
 * {@code products} must surface it on the {@code telaiometrics} actuator endpoint; {@code announcements}
 * sets {@code @DalMetrics(enabled = false)} and must therefore never appear. The endpoint reflects only
 * flushed buckets, so assertions poll until the data lands (test profile flushes every second).
 */
class MetricsEndpointIT extends AbstractShowcaseIT {

    private ResponseEntity<String> actuator(String path) {
        // /actuator/** is permitAll in the showcase, so no authentication is needed.
        return exchange(null, HttpMethod.GET, "http://localhost:" + port + "/actuator/" + path, null);
    }

    @Test
    void productOperationsAreRecordedAndAnnouncementsAreNot() {
        // Generate at least one recorded operation on the products DAL (reads are open to everyone).
        assertThat(list(USER, "products", "size=5").getStatusCode()).isEqualTo(HttpStatus.OK);

        // The endpoint exposes flushed buckets only — poll until the products summary appears.
        Awaitility.await("products metrics flushed")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                ResponseEntity<String> overview = actuator("telaiometrics");
                assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(productSummary(tree(overview))).isNotNull();
            });

        JsonNode overview = tree(actuator("telaiometrics"));

        // products is present with a positive operation count...
        JsonNode products = productSummary(overview);
        assertThat(products).isNotNull();
        assertThat(products.get("stats").get("count").asLong()).isPositive();

        // ...and announcements (metrics disabled) never is.
        for (JsonNode dal : overview.get("dals")) {
            assertThat(dal.get("dalName").asString())
                .as("announcements has @DalMetrics(enabled=false) and must not be recorded")
                .isNotEqualTo("announcements");
        }
    }

    @Test
    void perDalAndPerOperationDrillDownsAreExposed() {
        assertThat(list(USER, "products", "size=5").getStatusCode()).isEqualTo(HttpStatus.OK);

        Awaitility.await("products READ metrics flushed")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                ResponseEntity<String> dalView = actuator("telaiometrics/products");
                assertThat(dalView.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(tree(dalView).get("operations").has("READ")).isTrue();
            });

        // Per-DAL drill-down: overall + per-operation stats.
        JsonNode dalView = tree(actuator("telaiometrics/products"));
        assertThat(dalView.get("dalName").asString()).isEqualTo("products");
        assertThat(dalView.get("overall").get("count").asLong()).isPositive();
        assertThat(dalView.get("operations").has("READ")).isTrue();

        // Per-operation drill-down.
        JsonNode opView = tree(actuator("telaiometrics/products/read"));
        assertThat(opView.get("dalName").asString()).isEqualTo("products");
        assertThat(opView.get("operation").asString()).isEqualTo("READ");
        assertThat(opView.get("stats").get("count").asLong()).isPositive();
    }

    private JsonNode productSummary(JsonNode overview) {
        for (JsonNode dal : overview.get("dals")) {
            if ("products".equals(dal.get("dalName").asString())) {
                return dal;
            }
        }
        return null;
    }
}
