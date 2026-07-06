package com.paganbit.telaio.showcase.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

    private static final String SCHEME_NAME = "basicAuth";
    private static final String SCHEME = "basic";

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
            .components(new Components().addSecuritySchemes(SCHEME_NAME, createSecurityScheme()))
            .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }

    /**
     * Telaio-web registers a {@link GroupedOpenApi} for the DAL API, which switches springdoc into
     * grouped mode: the definition dropdown then lists only explicitly declared groups. This second
     * group surfaces the actuator endpoints (health, {@code telaiometrics}) — enabled via
     * {@code springdoc.show-actuator} — under their own definition.
     */
    @Bean
    GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
            .group("ACTUATOR")
            .pathsToMatch("/actuator/**")
            .build();
    }

    private SecurityScheme createSecurityScheme() {
        return new SecurityScheme()
            .name(SCHEME_NAME)
            .type(SecurityScheme.Type.HTTP)
            .scheme(SCHEME);
    }
}
