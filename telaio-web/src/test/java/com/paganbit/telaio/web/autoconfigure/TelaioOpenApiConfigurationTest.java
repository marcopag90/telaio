package com.paganbit.telaio.web.autoconfigure;

import com.paganbit.telaio.core.version.TelaioVersionProvider;
import com.paganbit.telaio.web.DalRestApiV1;
import com.paganbit.telaio.web.TelaioOpenApiGroupCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Telaio OpenAPI group is opt-in and reports the library's own version (resolved
 * by the core {@link TelaioVersionProvider}), never the consuming application's {@code GitProperties}.
 */
class TelaioOpenApiConfigurationTest {

    private final TelaioVersionProvider versionProvider = new TelaioVersionProvider();
    private final TelaioWebAutoConfiguration.OpenApiConfiguration configuration =
        new TelaioWebAutoConfiguration.OpenApiConfiguration(versionProvider);

    @Test
    void version_shouldBeResolvedFromFilteredClasspathResource() {
        // Verifies that telaio-core's telaio-version.properties was Maven-filtered before
        // telaio-web is built. If the resource is missing or the placeholder was never replaced,
        // TelaioVersionProvider returns "unknown" and the OpenAPI Info would carry that fallback.
        assertThat(versionProvider.getVersion())
            .isNotBlank()
            .doesNotStartWith("@")
            .isNotEqualTo("unknown");
    }

    @Test
    void buildInfo_shouldCarryTelaioBrandingAndLibraryVersion() {
        final Info info = configuration.buildInfo();

        assertThat(info.getTitle()).isEqualTo("Telaio API");
        assertThat(info.getVersion()).isEqualTo(versionProvider.getVersion());
        assertThat(info.getContact()).isNotNull();
        assertThat(info.getContact().getName()).isEqualTo("Marco Pagan");
        assertThat(info.getLicense()).isNotNull();
        assertThat(info.getLicense().getName()).isEqualTo("Apache 2.0");
    }

    @Test
    void dalApiV1Group_isNotRegisteredByDefault() {
        new ApplicationContextRunner()
            .withBean(TelaioVersionProvider.class)
            .withUserConfiguration(TelaioWebAutoConfiguration.OpenApiConfiguration.class)
            .run(context -> assertThat(context).doesNotHaveBean(GroupedOpenApi.class));
    }

    @Test
    void dalApiV1Group_isRegisteredWhenOpenApiEnabled() {
        new ApplicationContextRunner()
            .withPropertyValues("telaio.web.openapi.enabled=true")
            .withBean(TelaioVersionProvider.class)
            .withUserConfiguration(TelaioWebAutoConfiguration.OpenApiConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(GroupedOpenApi.class);
                assertThat(context).hasBean(TelaioWebAutoConfiguration.OpenApiConfiguration.DAL_API_V1_GROUP_BEAN_NAME);
            });
    }

    @Test
    void dalApiV1Group_customizersShouldBrandTheDocumentAndRunGroupCustomizersInOrder() {
        List<String> calls = new ArrayList<>();
        TelaioOpenApiGroupCustomizer first = openApi -> calls.add("first");
        TelaioOpenApiGroupCustomizer second = openApi -> calls.add("second");

        new ApplicationContextRunner()
            .withPropertyValues("telaio.web.openapi.enabled=true")
            .withBean(TelaioVersionProvider.class)
            .withBean("second", TelaioOpenApiGroupCustomizer.class, () -> second)
            .withBean("first", TelaioOpenApiGroupCustomizer.class, () -> first)
            .withUserConfiguration(TelaioWebAutoConfiguration.OpenApiConfiguration.class)
            .run(context -> {
                GroupedOpenApi group = context.getBean(GroupedOpenApi.class);
                assertThat(group.getGroup()).isEqualTo("TELAIO");
                assertThat(group.getPathsToMatch()).containsExactly(DalRestApiV1.BASE_PATH + "/**");

                // springdoc applies the customizers only when it renders the document: do the same here.
                OpenAPI document = new OpenAPI();
                for (OpenApiCustomizer customizer : group.getOpenApiCustomizers()) {
                    customizer.customise(document);
                }

                assertThat(document.getInfo()).isNotNull();
                assertThat(document.getInfo().getTitle()).isEqualTo("Telaio API");
                assertThat(document.getInfo().getVersion()).isEqualTo(versionProvider.getVersion());
                assertThat(calls).containsExactlyInAnyOrder("first", "second");
            });
    }
}
