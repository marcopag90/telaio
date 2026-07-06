package com.paganbit.telaio.web.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default values and binding behavior of {@link TelaioWebProperties}.
 */
class TelaioWebPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class);

    @Configuration
    @EnableConfigurationProperties(TelaioWebProperties.class)
    static class PropertiesConfig {
    }

    @Test
    void defaults_shouldReflectDocumentedValues() {
        contextRunner.run(context -> {
            TelaioWebProperties p = context.getBean(TelaioWebProperties.class);
            assertThat(p.getOpenapi().isEnabled()).isTrue();
        });
    }

    @Test
    void customProperties_shouldBindCorrectly() {
        contextRunner
            .withPropertyValues("telaio.web.openapi.enabled=false")
            .run(context -> {
                TelaioWebProperties p = context.getBean(TelaioWebProperties.class);
                assertThat(p.getOpenapi().isEnabled()).isFalse();
            });
    }
}
