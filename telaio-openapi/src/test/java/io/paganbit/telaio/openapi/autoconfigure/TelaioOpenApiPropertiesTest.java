package io.paganbit.telaio.openapi.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies defaults and {@code telaio.openapi} binding of {@link TelaioOpenApiProperties}.
 */
class TelaioOpenApiPropertiesTest {

    @Test
    void defaults_shouldEnableEverything() {
        TelaioOpenApiProperties properties = new TelaioOpenApiProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isIncludeExamples()).isFalse();
        assertThat(properties.isTagPerDal()).isTrue();
    }

    @Test
    void binding_shouldMapPrefixedProperties() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("telaio.openapi.enabled", "false")
            .withProperty("telaio.openapi.include-examples", "false")
            .withProperty("telaio.openapi.tag-per-dal", "false");
        Binder binder = new Binder(ConfigurationPropertySources.get(environment));

        TelaioOpenApiProperties properties = binder
            .bind("telaio.openapi", Bindable.of(TelaioOpenApiProperties.class))
            .orElseGet(TelaioOpenApiProperties::new);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isIncludeExamples()).isFalse();
        assertThat(properties.isTagPerDal()).isFalse();
    }
}
