package com.paganbit.telaio.rest.client.blocking.autoconfigure;

import com.paganbit.telaio.rest.client.blocking.TelaioClient;
import com.paganbit.telaio.rest.client.blocking.TelaioClientRegistry;
import com.paganbit.telaio.rest.client.blocking.TelaioRestClientCustomizer;
import com.paganbit.telaio.rest.client.config.SingleConnectionCondition;
import com.paganbit.telaio.rest.client.config.TelaioRestClientProperties;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio REST client.
 *
 * <p>Registers the {@link TelaioClientRegistry} over the connections configured under
 * {@code telaio.rest-client.connections.<name>}, and — when one connection is unambiguously primary
 * (a single connection, or one named {@code default}) — a convenience {@link TelaioClient} bean.
 * Each connection builds on Spring Boot's autoconfigured {@link RestClient.Builder}, inheriting
 * {@code spring.http.client.*} settings, SSL and every {@code RestClientCustomizer};
 * per-connection concerns (authentication above all) plug in via
 * {@link TelaioRestClientCustomizer} beans.</p>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
@AutoConfiguration(
    after = RestClientAutoConfiguration.class,
    afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
)
@ConditionalOnClass(
    value = RestClient.class,
    name = "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration"
)
@EnableConfigurationProperties(TelaioRestClientProperties.class)
public class TelaioRestClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TelaioClientRegistry telaioClientRegistry(
        TelaioRestClientProperties properties,
        ObjectProvider<RestClient.Builder> restClientBuilder,
        ObjectProvider<ObjectMapper> objectMapper,
        ObjectProvider<FilterStringConverter> filterStringConverters,
        ObjectProvider<TelaioRestClientCustomizer> customizers
    ) {
        return new DefaultTelaioClientRegistry(
            properties, restClientBuilder, objectMapper, filterStringConverters, customizers);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(SingleConnectionCondition.class)
    TelaioClient telaioClient(TelaioClientRegistry registry, TelaioRestClientProperties properties) {
        Set<String> connections = properties.getConnections().keySet();
        String primary = connections.contains(TelaioRestClientProperties.DEFAULT_CONNECTION_NAME)
            ? TelaioRestClientProperties.DEFAULT_CONNECTION_NAME
            : connections.iterator().next();
        return registry.get(primary);
    }
}
