package io.paganbit.telaio.openapi.autoconfigure;

import io.paganbit.telaio.core.registry.DalManager;
import io.paganbit.telaio.openapi.customizer.DalOpenApiCustomizer;
import io.paganbit.telaio.openapi.generator.DalPathsGenerator;
import io.paganbit.telaio.openapi.generator.FilterParameterDescriber;
import io.paganbit.telaio.openapi.introspection.DalEntitySchemaResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the conditional wiring of {@link TelaioOpenApiAutoConfiguration}.
 */
class TelaioOpenApiAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TelaioOpenApiAutoConfiguration.class))
        .withBean("objectMapper", ObjectMapper.class, () -> JsonMapper.builder().build());

    @Test
    void byDefault_withDalManager_shouldRegisterBeans() {
        contextRunner
            .withBean(DalManager.class, () -> mock(DalManager.class))
            .run(context -> {
                assertThat(context).hasSingleBean(DalOpenApiCustomizer.class);
                assertThat(context).hasSingleBean(DalPathsGenerator.class);
                assertThat(context).hasSingleBean(FilterParameterDescriber.class);
                assertThat(context).hasSingleBean(DalEntitySchemaResolver.class);
            });
    }

    @Test
    void whenDisabled_shouldNotRegisterCustomizer() {
        contextRunner
            .withBean(DalManager.class, () -> mock(DalManager.class))
            .withPropertyValues("telaio.openapi.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(DalOpenApiCustomizer.class));
    }

    @Test
    void withoutDalManager_shouldNotRegisterCustomizer() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DalOpenApiCustomizer.class));
    }

    @Test
    void customCustomizer_shouldReplaceDefault() {
        DalOpenApiCustomizer custom = mock(DalOpenApiCustomizer.class);
        contextRunner
            .withBean(DalManager.class, () -> mock(DalManager.class))
            .withBean(DalOpenApiCustomizer.class, () -> custom)
            .run(context -> assertThat(context.getBean(DalOpenApiCustomizer.class)).isSameAs(custom));
    }

    @Test
    void nonServletContext_shouldNotLoadAutoConfiguration() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TelaioOpenApiAutoConfiguration.class))
            .withBean(DalManager.class, () -> mock(DalManager.class))
            .run(context -> assertThat(context).doesNotHaveBean(DalOpenApiCustomizer.class));
    }
}
