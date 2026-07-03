package io.paganbit.telaio.web;

import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * SPI marker for springdoc {@link OpenApiCustomizer}s that must be applied to the branded {@code TELAIO}
 * {@link org.springdoc.core.models.GroupedOpenApi} group <em>only</em> — never globally to every group.
 *
 * <p>The {@code TELAIO} group bean ({@code dalApiV1Group}) collects every bean of this type and registers
 * them as <em>group-scoped</em> customizers via {@link org.springdoc.core.models.GroupedOpenApi.Builder
 * #addOpenApiCustomizer}. Modules such as {@code telaio-openapi} implement this to enrich the DAL API
 * documentation inside the TELAIO group without leaking their changes into a consuming application's own
 * OpenAPI groups — a plain {@code OpenApiCustomizer} bean would otherwise reach only the default document,
 * and a {@code GlobalOpenApiCustomizer} would pollute every group.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface TelaioOpenApiGroupCustomizer extends OpenApiCustomizer {
}
