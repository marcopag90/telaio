package com.paganbit.telaio.openapi.customizer;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.registry.DalCatalog;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.openapi.generator.DalPathsGenerator;
import com.paganbit.telaio.openapi.introspection.DalEntitySchemaResolver;
import com.paganbit.telaio.web.DalRestApiV1;
import com.paganbit.telaio.web.TelaioOpenApiGroupCustomizer;
import com.paganbit.telaio.web.channel.DalRestChannelDefinition;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;

import java.util.HashSet;
import java.util.Set;

/**
 * Springdoc customizer that replaces the opaque, generic operations springdoc derives from the dynamic
 * {@link DalRestApiV1} controller with concrete, per-DAL operations describing each registered DAL's entity,
 * id, and error responses.
 *
 * <p>It removes the two templated paths ({@code /dal/v1/{dalName}} and {@code /dal/v1/{dalName}/{id}}),
 * then iterates the {@link DalCatalog} and delegates to {@link DalPathsGenerator} to synthesize a typed
 * path-set for every DAL on the REST channel, documenting only the operations the DAL exposes there as
 * answered by the {@link DalRestChannelDefinition} (its {@code @DalWeb}). Internal DALs and DALs that
 * expose nothing on REST are not documented at all.</p>
 *
 * <p>It is a {@link TelaioOpenApiGroupCustomizer} — a plain (non-global) {@code OpenApiCustomizer}. Springdoc
 * therefore applies it to the <em>default</em> {@code /v3/api-docs} document (the natural place for a
 * non-grouped application), while the branded {@code TELAIO} {@code GroupedOpenApi} adds it as a
 * group-scoped customizer. Crucially it is <em>not</em> a {@code GlobalOpenApiCustomizer}, so it never
 * leaks the DAL operations into a consuming application's own OpenAPI groups.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalOpenApiCustomizer implements TelaioOpenApiGroupCustomizer {

    private static final Logger log = LoggerFactory.getLogger(DalOpenApiCustomizer.class);

    private final DalCatalog catalog;
    private final DalRestChannelDefinition restChannel;
    private final DalPathsGenerator pathsGenerator;
    private final DalEntitySchemaResolver schemaResolver;

    public DalOpenApiCustomizer(
        DalCatalog catalog,
        DalRestChannelDefinition restChannel,
        DalPathsGenerator pathsGenerator,
        DalEntitySchemaResolver schemaResolver
    ) {
        this.catalog = catalog;
        this.restChannel = restChannel;
        this.pathsGenerator = pathsGenerator;
        this.schemaResolver = schemaResolver;
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            openApi.setPaths(new Paths());
        }
        schemaResolver.ensureComponents(openApi);
        removeGenericTemplatedPaths(openApi);

        // Register the shared RFC 9457 error-body schema once and reuse the $ref across every operation's
        // 400/404/500 responses.
        Schema<?> problemDetailRef = schemaResolver.resolveAndRegister(ProblemDetail.class, openApi);

        for (DalDefinitionEntry entry : catalog.getAllDefinitions()) {
            if (entry.internal()) {
                // Internal DALs are on no channel: they are not documented.
                log.debug("DAL '{}' is internal — not documented in OpenAPI", entry.name());
                continue;
            }
            Set<DalOperationType> exposed = restChannel.exposedOperations(entry.dalClass());
            if (exposed.isEmpty()) {
                // Not on REST: no endpoint, so nothing to document.
                log.debug("DAL '{}' exposes no operation on REST — not documented in OpenAPI", entry.name());
                continue;
            }
            try {
                Dal<?, ?> dal = catalog.getServiceByName(entry.name());
                pathsGenerator.generate(openApi, entry.name(), dal.getEntityClass(), dal.getIdClass(),
                    problemDetailRef, exposed);
            } catch (RuntimeException ex) {
                // A single misbehaving DAL must not break documentation for all the others.
                log.warn("Skipping OpenAPI generation for DAL '{}': {}", entry.name(), ex.getMessage());
            }
        }

        pruneUnusedTags(openApi);
    }

    /**
     * Removes the generic, templated operations springdoc generated from the dynamic controller so the
     * synthesized per-DAL operations stand alone.
     */
    private void removeGenericTemplatedPaths(OpenAPI openApi) {
        String collectionPath = DalRestApiV1.BASE_PATH + "/{" + DalRestApiV1.PATH_VARIABLE_DAL_NAME + "}";
        String itemPath = collectionPath + "/{" + DalRestApiV1.PATH_VARIABLE_ID + "}";
        openApi.getPaths().remove(collectionPath);
        openApi.getPaths().remove(itemPath);
    }

    /**
     * Drops top-level tags that no operation references — notably the dynamic controller's
     * {@code @Tag("DAL API v1")}, which springdoc adds to the document but which becomes orphaned once the
     * generic operations are removed, otherwise surfacing as an empty, non-expandable section in Swagger UI.
     */
    private void pruneUnusedTags(OpenAPI openApi) {
        if (openApi.getTags() == null || openApi.getTags().isEmpty()) {
            return;
        }
        Set<String> usedTags = new HashSet<>();
        for (PathItem pathItem : openApi.getPaths().values()) {
            for (Operation operation : pathItem.readOperations()) {
                if (operation.getTags() != null) {
                    usedTags.addAll(operation.getTags());
                }
            }
        }
        openApi.getTags().removeIf(tag -> !usedTags.contains(tag.getName()));
        if (openApi.getTags().isEmpty()) {
            openApi.setTags(null);
        }
    }
}
