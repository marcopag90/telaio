package com.paganbit.telaio.metrics.autoconfigure;

import com.paganbit.telaio.metrics.endpoint.TelaioMetricsEndpoint;
import com.paganbit.telaio.metrics.store.DalMetricsQueryService;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.time.Clock;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for the {@code telaiometrics} actuator
 * endpoint.
 *
 * <p>Registered as a separate class — guarded by {@link ConditionalOnClass} — so it never loads
 * when Spring Boot Actuator is absent. The endpoint is created only when it is exposed and a
 * {@link DalMetricsQueryService} (provided by the metrics autoconfiguration) is available. It reads
 * the in-house store, so it is bound to the in-house path via {@link OnInHouseMetricsCondition}:
 * when the Micrometer recorder takes over, metrics are exposed through Micrometer instead and this
 * endpoint stands aside.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@AutoConfiguration(after = {TelaioMetricsAutoConfiguration.class})
@ConditionalOnClass(Endpoint.class)
@Conditional(OnInHouseMetricsCondition.class)
@EnableConfigurationProperties(TelaioMetricsProperties.class)
public class TelaioMetricsEndpointAutoConfiguration {

    @Bean
    @ConditionalOnAvailableEndpoint(TelaioMetricsEndpoint.class)
    @ConditionalOnBean(DalMetricsQueryService.class)
    @ConditionalOnMissingBean
    TelaioMetricsEndpoint telaioMetricsEndpoint(
        DalMetricsQueryService queryService,
        TelaioMetricsProperties properties
    ) {
        return new TelaioMetricsEndpoint(queryService, properties.getEndpoint(), Clock.systemUTC());
    }
}
