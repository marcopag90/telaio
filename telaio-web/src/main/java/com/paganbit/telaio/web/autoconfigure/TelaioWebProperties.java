package com.paganbit.telaio.web.autoconfigure;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Telaio Web, bound to the {@code telaio.web} prefix.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@ConfigurationProperties("telaio.web")
@Validated
public class TelaioWebProperties {

    @Valid
    private final OpenApi openapi = new OpenApi();

    public OpenApi getOpenapi() {
        return openapi;
    }

    /**
     * Settings for the Telaio OpenAPI group.
     */
    public static class OpenApi {

        /**
         * Whether to register the branded {@code TELAIO} OpenAPI group. On by default: enabling it
         * declares a springdoc {@code GroupedOpenApi}, which switches springdoc into grouped mode,
         * so the consuming application must then declare its own groups to keep its endpoints
         * visible.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
