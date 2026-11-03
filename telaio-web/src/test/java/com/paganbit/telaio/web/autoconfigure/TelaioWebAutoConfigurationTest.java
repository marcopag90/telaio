package com.paganbit.telaio.web.autoconfigure;

import com.paganbit.telaio.core.adapter.DalOperationAdapterRegistry;
import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.web.annotation.DalIdArgumentResolver;
import com.paganbit.telaio.web.interceptor.WebDalExposureInterceptorProvider;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the bean registration and conditional wiring of {@link TelaioWebAutoConfiguration}.
 */
class TelaioWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            TelaioCoreAutoConfiguration.class,
            TelaioWebAutoConfiguration.class))
        .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new)
        .withBean(SpringValidatorAdapter.class,
            () -> new SpringValidatorAdapter(mock(jakarta.validation.Validator.class)))
        .withBean(FilterStringConverter.class, () -> mock(FilterStringConverter.class));

    @Test
    void byDefault_shouldRegisterWebBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DalIdArgumentResolver.class);
            assertThat(context).hasSingleBean(WebDalExposureInterceptorProvider.class);
            assertThat(context).hasSingleBean(DalOperationAdapterRegistry.class);
        });
    }

    @Test
    void reactiveWebContext_shouldNotLoadTelaioWebAutoConfiguration() {
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TelaioWebAutoConfiguration.class))
            .run(context ->
                assertThat(context).doesNotHaveBean(DalIdArgumentResolver.class));
    }
}
