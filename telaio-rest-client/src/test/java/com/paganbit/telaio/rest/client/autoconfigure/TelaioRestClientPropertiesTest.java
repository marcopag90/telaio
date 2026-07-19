package com.paganbit.telaio.rest.client.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelaioRestClientPropertiesTest {

    @Test
    void bindsConnectionsMapWithHeaders() {
        Map<String, String> source = Map.of(
            "telaio.rest-client.connections.billing.base-url", "https://billing.example.com",
            "telaio.rest-client.connections.billing.default-headers.X-Tenant[0]", "acme",
            "telaio.rest-client.connections.inventory.base-url", "https://inventory.example.com");

        TelaioRestClientProperties properties = new Binder(new MapConfigurationPropertySource(source))
            .bind(TelaioRestClientProperties.PREFIX, TelaioRestClientProperties.class)
            .get();

        assertThat(properties.getConnections()).containsOnlyKeys("billing", "inventory");
        TelaioRestClientProperties.Connection billing = properties.getConnections().get("billing");
        assertThat(billing.getBaseUrl()).isEqualTo("https://billing.example.com");
        assertThat(billing.getDefaultHeaders()).containsEntry("X-Tenant", List.of("acme"));
        assertThat(properties.getConnections().get("inventory").getDefaultHeaders()).isEmpty();
    }

    @Test
    void bindsToEmptyWhenNothingConfigured() {
        TelaioRestClientProperties properties = new Binder(new MapConfigurationPropertySource(Map.of()))
            .bind(TelaioRestClientProperties.PREFIX, TelaioRestClientProperties.class)
            .orElseGet(TelaioRestClientProperties::new);

        assertThat(properties.getConnections()).isEmpty();
    }
}
