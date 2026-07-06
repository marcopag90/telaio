package com.paganbit.telaio.web.autoconfigure;

import com.turkraft.springfilter.converter.FilterStringConverter;
import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.web.annotation.DalIdArgumentResolver;
import com.paganbit.telaio.web.registry.DefaultWebDalOperationAdapterRegistry;
import com.paganbit.telaio.web.registry.WebDalOperationAdapterAssembler;
import com.paganbit.telaio.web.registry.WebDalOperationAdapterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the bean registration and conditional wiring of {@link TelaioWebAutoConfiguration}.
 */
class TelaioWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            TelaioCoreAutoConfiguration.class,
            TelaioWebAutoConfiguration.class))
        .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new)
        .withBean("objectMapper", ObjectMapper.class, () -> JsonMapper.builder().build())
        .withBean(FilterStringConverter.class, () -> mock(FilterStringConverter.class));

    @Test
    void byDefault_shouldRegisterCoreWebBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(WebDalOperationAdapterRegistry.class);
            assertThat(context.getBean(WebDalOperationAdapterRegistry.class))
                .isInstanceOf(DefaultWebDalOperationAdapterRegistry.class);
            assertThat(context).hasSingleBean(WebDalOperationAdapterAssembler.class);
            assertThat(context).hasSingleBean(DalIdArgumentResolver.class);
        });
    }

    @Test
    void customAdapterRegistry_shouldReplaceDefault() {
        contextRunner
            .withBean(WebDalOperationAdapterRegistry.class, () -> mock(WebDalOperationAdapterRegistry.class))
            .run(context -> {
                assertThat(context).hasSingleBean(WebDalOperationAdapterRegistry.class);
                assertThat(context).doesNotHaveBean(DefaultWebDalOperationAdapterRegistry.class);
            });
    }

    @Test
    void reactiveWebContext_shouldNotLoadTelaioWebAutoConfiguration() {
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TelaioWebAutoConfiguration.class))
            .run(context -> assertThat(context).doesNotHaveBean(WebDalOperationAdapterRegistry.class));
    }
}
