package io.paganbit.telaio.openapi.autoconfigure;

import io.paganbit.telaio.core.json.JsonPropertyPathResolver;
import io.paganbit.telaio.core.registry.DalManager;
import io.paganbit.telaio.openapi.customizer.DalOpenApiCustomizer;
import io.paganbit.telaio.openapi.generator.DalPathsGenerator;
import io.paganbit.telaio.openapi.generator.FilterParameterDescriber;
import io.paganbit.telaio.openapi.introspection.DalEntitySchemaResolver;
import io.paganbit.telaio.web.autoconfigure.TelaioWebAutoConfiguration;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio OpenAPI.
 *
 * <p>Registers a springdoc {@link OpenApiCustomizer} that auto-generates concrete, per-DAL operations for
 * the dynamic Telaio REST endpoints. Active only in a servlet web application that has springdoc on the
 * classpath and a {@link DalManager} bean, and unless {@code telaio.openapi.enabled=false}. Every
 * collaborator is a {@code @ConditionalOnMissingBean} so consumers can override any single piece.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@AutoConfiguration(after = TelaioWebAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenApiCustomizer.class)
@ConditionalOnBean(DalManager.class)
@ConditionalOnProperty(prefix = "telaio.openapi", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(TelaioOpenApiProperties.class)
public class TelaioOpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JsonPropertyPathResolver dalJsonPropertyPathResolver(ObjectMapper objectMapper) {
        return new JsonPropertyPathResolver(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    DalEntitySchemaResolver dalEntitySchemaResolver(JsonPropertyPathResolver jsonPathResolver) {
        return new DalEntitySchemaResolver(jsonPathResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    FilterParameterDescriber dalFilterParameterDescriber(
        JsonPropertyPathResolver jsonPathResolver,
        TelaioOpenApiProperties properties
    ) {
        return new FilterParameterDescriber(jsonPathResolver, properties.isIncludeExamples());
    }

    @Bean
    @ConditionalOnMissingBean
    DalPathsGenerator dalPathsGenerator(
        DalEntitySchemaResolver schemaResolver,
        FilterParameterDescriber filterDescriber,
        ObjectMapper objectMapper,
        TelaioOpenApiProperties properties
    ) {
        return new DalPathsGenerator(schemaResolver, filterDescriber, objectMapper, properties.isTagPerDal());
    }

    @Bean
    @ConditionalOnMissingBean
    DalOpenApiCustomizer dalOpenApiCustomizer(
        DalManager dalManager,
        DalPathsGenerator pathsGenerator,
        DalEntitySchemaResolver schemaResolver
    ) {
        return new DalOpenApiCustomizer(dalManager, pathsGenerator, schemaResolver);
    }
}
