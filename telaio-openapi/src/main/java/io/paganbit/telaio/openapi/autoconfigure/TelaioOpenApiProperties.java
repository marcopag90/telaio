package io.paganbit.telaio.openapi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Telaio OpenAPI, bound to the {@code telaio.openapi} prefix.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@ConfigurationProperties("telaio.openapi")
@Validated
public class TelaioOpenApiProperties {

    /**
     * Whether per-DAL OpenAPI operations are auto-generated. On by default: this contributes a plain
     * springdoc {@code OpenApiCustomizer} (not a {@code GroupedOpenApi}), so it only enriches the
     * specification springdoc already builds and never switches springdoc into grouped mode.
     */
    private boolean enabled = true;

    /**
     * Whether a ready-to-paste example filter, derived from each entity's fields, is attached to the
     * generated {@code q} filter parameter.
     */
    private boolean includeExamples = false;

    /**
     * Whether each DAL's operations are tagged with the DAL name, so Swagger UI groups them per DAL.
     * When {@code false}, all generated operations share a single {@code DAL} tag.
     */
    private boolean tagPerDal = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIncludeExamples() {
        return includeExamples;
    }

    public void setIncludeExamples(boolean includeExamples) {
        this.includeExamples = includeExamples;
    }

    public boolean isTagPerDal() {
        return tagPerDal;
    }

    public void setTagPerDal(boolean tagPerDal) {
        this.tagPerDal = tagPerDal;
    }
}
