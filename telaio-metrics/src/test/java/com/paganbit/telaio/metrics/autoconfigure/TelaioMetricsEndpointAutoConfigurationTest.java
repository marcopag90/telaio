package com.paganbit.telaio.metrics.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.metrics.endpoint.TelaioMetricsEndpoint;
import com.paganbit.telaio.metrics.store.DalMetricsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the conditional wiring of {@link TelaioMetricsEndpointAutoConfiguration}.
 */
class TelaioMetricsEndpointAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            TelaioCoreAutoConfiguration.class,
            TelaioMetricsAutoConfiguration.class,
            TelaioMetricsEndpointAutoConfiguration.class))
        .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new);

    @Test
    void byDefault_endpointIsNotRegistered() {
        // @ConditionalOnAvailableEndpoint: not exposed → no bean
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(TelaioMetricsEndpoint.class));
    }

    @Test
    void whenExposed_endpointIsRegistered() {
        contextRunner
            .withPropertyValues("management.endpoints.web.exposure.include=telaiometrics")
            .run(context -> assertThat(context).hasSingleBean(TelaioMetricsEndpoint.class));
    }

    @Test
    void withoutActuator_endpointIsNotRegistered() {
        // @ConditionalOnClass(Endpoint.class): Actuator absent → entire autoconfiguration skipped
        contextRunner
            .withClassLoader(new FilteredClassLoader(Endpoint.class))
            .withPropertyValues("management.endpoints.web.exposure.include=telaiometrics")
            .run(context -> assertThat(context).doesNotHaveBean(TelaioMetricsEndpoint.class));
    }

    @Test
    void withoutQueryService_endpointIsNotRegistered() {
        // @ConditionalOnBean(DalMetricsQueryService.class): no store → no endpoint
        contextRunner
            .withPropertyValues(
                "management.endpoints.web.exposure.include=telaiometrics",
                "telaio.metrics.in-house.enabled=false")
            .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
            .run(context -> {
                assertThat(context).doesNotHaveBean(DalMetricsQueryService.class);
                assertThat(context).doesNotHaveBean(TelaioMetricsEndpoint.class);
            });
    }

    @Test
    void micrometerTakesOver_inHouseEndpointIsNotRegistered() {
        // @Conditional(OnInHouseMetricsCondition): Micrometer active, in-house is off → endpoint not created
        contextRunner
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues(
                "management.endpoints.web.exposure.include=telaiometrics",
                "telaio.metrics.micrometer.enabled=true")
            .run(context -> assertThat(context).doesNotHaveBean(TelaioMetricsEndpoint.class));
    }

    @Test
    void customEndpoint_backsOffAutoConfiguredOne() {
        // @ConditionalOnMissingBean: user-provided endpoint wins
        contextRunner
            .withBean(TelaioMetricsEndpoint.class, () -> mock(TelaioMetricsEndpoint.class))
            .withPropertyValues("management.endpoints.web.exposure.include=telaiometrics")
            .run(context -> assertThat(context).hasSingleBean(TelaioMetricsEndpoint.class));
    }
}
