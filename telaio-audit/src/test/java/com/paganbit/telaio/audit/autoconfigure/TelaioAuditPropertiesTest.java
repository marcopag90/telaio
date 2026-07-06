package com.paganbit.telaio.audit.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default values and binding behavior of {@link TelaioAuditProperties}, and ensures
 * that Bean Validation rejects an invalid configuration at context startup.
 */
class TelaioAuditPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class);

    @Configuration
    @EnableConfigurationProperties(TelaioAuditProperties.class)
    static class PropertiesConfig {
    }

    @Test
    void defaults_shouldReflectDocumentedValues() {
        contextRunner.run(context -> {
            TelaioAuditProperties.Logging logging =
                context.getBean(TelaioAuditProperties.class).getLogging();

            assertThat(logging.getFormat()).isEqualTo(TelaioAuditProperties.Format.TEXT);
            assertThat(logging.getCategory()).isEqualTo("com.paganbit.telaio.audit.AUDIT");
            assertThat(logging.isIncludeMdc()).isTrue();
        });
    }

    @Test
    void customProperties_shouldBindCorrectly() {
        contextRunner
            .withPropertyValues(
                "telaio.audit.logging.format=JSON",
                "telaio.audit.logging.category=my.audit.category",
                "telaio.audit.logging.include-mdc=false"
            )
            .run(context -> {
                TelaioAuditProperties.Logging logging =
                    context.getBean(TelaioAuditProperties.class).getLogging();

                assertThat(logging.getFormat()).isEqualTo(TelaioAuditProperties.Format.JSON);
                assertThat(logging.getCategory()).isEqualTo("my.audit.category");
                assertThat(logging.isIncludeMdc()).isFalse();
            });
    }

    @Test
    void format_shouldBindCaseInsensitively() {
        contextRunner
            .withPropertyValues("telaio.audit.logging.format=json")
            .run(context -> assertThat(context.getBean(TelaioAuditProperties.class)
                .getLogging().getFormat()).isEqualTo(TelaioAuditProperties.Format.JSON));
    }

    @Test
    void blankCategory_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.audit.logging.category=")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unknownFormat_shouldFailContextStartup() {
        contextRunner
            .withPropertyValues("telaio.audit.logging.format=xml")
            .run(context -> assertThat(context).hasFailed());
    }
}
