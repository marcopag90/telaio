package io.paganbit.telaio.web.autoconfigure;

import io.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import io.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import io.paganbit.telaio.core.registry.DalManager;
import io.paganbit.telaio.core.version.TelaioVersionProvider;
import io.paganbit.telaio.web.DalRestApiV1;
import io.paganbit.telaio.web.DalRestApiV1Controller;
import io.paganbit.telaio.web.TelaioOpenApiGroupCustomizer;
import io.paganbit.telaio.web.annotation.DalIdArgumentResolver;
import io.paganbit.telaio.web.exception.TelaioAccessDeniedExceptionHandler;
import io.paganbit.telaio.web.exception.TelaioWebExceptionHandler;
import io.paganbit.telaio.web.interceptor.WebDalExposureInterceptorProvider;
import io.paganbit.telaio.web.registry.DefaultWebDalOperationAdapterRegistry;
import io.paganbit.telaio.web.registry.WebDalOperationAdapterAssembler;
import io.paganbit.telaio.web.registry.WebDalOperationAdapterRegistry;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio Web
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@AutoConfiguration(
    after = TelaioCoreAutoConfiguration.class,
    afterName = "io.paganbit.telaio.security.autoconfigure.TelaioSecurityAutoConfiguration"
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({DalRestApiV1Controller.class, TelaioWebExceptionHandler.class})
@EnableConfigurationProperties(TelaioWebProperties.class)
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class TelaioWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WebDalOperationAdapterRegistry dalOperationAdapterRegistry() {
        return new DefaultWebDalOperationAdapterRegistry();
    }

    /**
     * Enforces per-operation exposure ({@code @DalService(operations = ...)}) at the REST boundary as the
     * outermost interceptor, so calls to non-exposed operations are rejected
     * before any other concern runs.
     */
    @Bean
    WebDalExposureInterceptorProvider dalExposureInterceptorProvider() {
        return new WebDalExposureInterceptorProvider();
    }

    @Bean
    @ConditionalOnBean(DalManager.class)
    WebDalOperationAdapterAssembler dalOperationAdapterAssembler(
        DalManager dalManager,
        WebDalOperationAdapterRegistry registry,
        List<DalAdapterInterceptorProvider> interceptorProviders
    ) {
        return new WebDalOperationAdapterAssembler(dalManager, registry, interceptorProviders);
    }

    @Bean
    DalIdArgumentResolver dalIdArgumentResolver(
        DalManager dalManager,
        ObjectMapper objectMapper
    ) {
        return new DalIdArgumentResolver(dalManager, objectMapper);
    }

    /**
     * Maps Spring Security's {@code AccessDeniedException} to a generic {@code 403} {@link ProblemDetail},
     * registered only when Spring Security is on the classpath so the web module needs it merely
     * {@code optional}. Kept separate from {@link TelaioWebExceptionHandler} so that core handler never
     * references a Spring Security type.
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    TelaioAccessDeniedExceptionHandler telaioAccessDeniedExceptionHandler() {
        return new TelaioAccessDeniedExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    static class TelaioWebMvcConfiguration implements WebMvcConfigurer {

        private final DalIdArgumentResolver dalIdArgumentResolver;

        public TelaioWebMvcConfiguration(DalIdArgumentResolver dalIdArgumentResolver) {
            this.dalIdArgumentResolver = dalIdArgumentResolver;
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(dalIdArgumentResolver);
        }
    }

    /**
     * Registers the branded {@code TELAIO} {@link GroupedOpenApi} group only when
     * {@code telaio.web.openapi.enabled=true}. Opt-in because declaring any {@link GroupedOpenApi}
     * switches springdoc into grouped mode, hiding a consuming application's own endpoints until it
     * declares its own groups. The group's version is the Telaio library's own version resolved by
     * {@link TelaioVersionProvider}, never the consuming application's {@code GitProperties}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GroupedOpenApi.class)
    @ConditionalOnProperty(prefix = "telaio.web.openapi", name = "enabled", havingValue = "true")
    static class OpenApiConfiguration {

        public static final String DAL_API_V1_GROUP_BEAN_NAME = "dalApiV1Group";

        private static final String GROUP_NAME = "TELAIO";
        private static final String INFO_TITLE = "Telaio API";
        private static final String CONTACT_NAME = "Marco Pagan";
        private static final String CONTACT_EMAIL = "marcopag90@gmail.com";
        private static final String CONTACT_URL = "https://github.com/marcopag90/telaio";
        private static final String LICENSE_NAME = "Apache 2.0";
        private static final String LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0.html";

        private final TelaioVersionProvider versionProvider;

        OpenApiConfiguration(TelaioVersionProvider versionProvider) {
            this.versionProvider = versionProvider;
        }

        /**
         * Builds the branded {@code TELAIO} group for the DAL API. Beyond the branded {@code Info}, it
         * registers every {@link TelaioOpenApiGroupCustomizer} bean as a <em>group-scoped</em> customizer,
         * so modules like {@code telaio-openapi} can enrich the DAL documentation inside this group only —
         * without leaking into a consuming application's other OpenAPI groups.
         */
        @Bean(DAL_API_V1_GROUP_BEAN_NAME)
        GroupedOpenApi dalApiV1Group(ObjectProvider<TelaioOpenApiGroupCustomizer> groupCustomizers) {
            GroupedOpenApi.Builder builder = GroupedOpenApi.builder()
                .group(GROUP_NAME)
                .pathsToMatch(DalRestApiV1.BASE_PATH + "/**")
                .addOpenApiCustomizer(openApi -> openApi.info(buildInfo()));
            groupCustomizers.orderedStream().forEach(builder::addOpenApiCustomizer);
            return builder.build();
        }

        Info buildInfo() {
            return new Info()
                .title(INFO_TITLE)
                .version(versionProvider.getVersion())
                .contact(buildContact())
                .license(buildLicense());
        }

        Contact buildContact() {
            return new Contact()
                .name(CONTACT_NAME)
                .email(CONTACT_EMAIL)
                .url(CONTACT_URL);
        }

        License buildLicense() {
            return new License()
                .name(LICENSE_NAME)
                .url(LICENSE_URL);
        }
    }
}
