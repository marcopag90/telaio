package com.paganbit.telaio.openapi.generator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.introspection.TypeUtil;
import com.paganbit.telaio.openapi.introspection.DalEntitySchemaResolver;
import com.paganbit.telaio.web.DalRestApiV1;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.utils.Constants;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Synthesizes a concrete set of OpenAPI operations for a single DAL, mirroring the five operations the
 * generic {@link DalRestApiV1} controller routes dynamically — but typed to the DAL's actual entity and
 * id, with real request/response schemas, a documented {@code q} filter parameter, pagination parameters,
 * and the standard error responses.
 *
 * <p>Two paths are produced per DAL: a collection path ({@code /dal/v1/<name>}) carrying create (POST) and
 * read-page (GET), and an item path ({@code /dal/v1/<name>/{id}}) carrying read-one (GET), update (PATCH)
 * and delete (DELETE). Operations are tagged per DAL when {@code tagPerDal} is enabled, so Swagger UI
 * groups each DAL's operations together.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalPathsGenerator {

    private final DalEntitySchemaResolver schemaResolver;
    private final FilterParameterDescriber filterDescriber;
    private final ObjectMapper objectMapper;
    private final boolean tagPerDal;

    private static final String DEFAULT_TAG = "DAL";
    private static final String ACCESS_DENIED_MESSAGE = "Access denied";
    private static final String CONCURRENT_MODIFICATION_MESSAGE =
        "Concurrent modification of a versioned entity — re-read and retry";
    private static final String DAL_SERVICE_OR_ENTITY_NOT_FOUND_MESSAGE = "DAL service or entity not found";
    private static final String MALFORMED_READ_REQUEST_MESSAGE = "Malformed filter or pagination parameters";
    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected error";

    public DalPathsGenerator(
        DalEntitySchemaResolver schemaResolver,
        FilterParameterDescriber filterDescriber,
        ObjectMapper objectMapper,
        boolean tagPerDal
    ) {
        this.schemaResolver = schemaResolver;
        this.filterDescriber = filterDescriber;
        this.objectMapper = objectMapper;
        this.tagPerDal = tagPerDal;
    }

    /**
     * Adds the collection and item paths for one DAL to the document, documenting only the
     * {@code exposedOperations}. An operation a DAL does not expose is omitted; a path item that ends up
     * with no operation (e.g., the item path when only collection operations are exposed) is not added at
     * all.
     *
     * @param openApi           the document to enrich (paths and components are assumed initialized)
     * @param dalName           the DAL registration name (the {@code dalName} path segment)
     * @param entityClass       the DAL's managed entity type
     * @param idClass           the DAL's identifier type
     * @param problemDetailRef  a {@code $ref} schema for the RFC 9457 {@code ProblemDetail} error body,
     *                          shared across every {@code 400}/{@code 404}/{@code 500} response
     * @param exposedOperations the CRUD operations the DAL exposes on the remote boundary
     */
    @SuppressWarnings("squid:S1075")
    public void generate(
        OpenAPI openApi,
        String dalName,
        Class<?> entityClass,
        Class<?> idClass,
        Schema<?> problemDetailRef,
        Set<DalOperationType> exposedOperations
    ) {
        Schema<?> entityRef = schemaResolver.resolveAndRegister(entityClass, openApi);
        String tag = tagPerDal ? capitalize(dalName) : DEFAULT_TAG;
        String collectionPath = "%s/%s".formatted(DalRestApiV1.BASE_PATH, dalName);
        String itemPath = "%s/{%s}".formatted(collectionPath, DalRestApiV1.PATH_VARIABLE_ID);

        PathItem collection = new PathItem();
        if (exposedOperations.contains(DalOperationType.CREATE)) {
            collection.post(createOperation(dalName, tag, entityRef, problemDetailRef));
        }
        if (exposedOperations.contains(DalOperationType.READ)) {
            collection.get(readOperation(dalName, tag, entityClass, entityRef, problemDetailRef));
        }

        PathItem item = new PathItem();
        if (exposedOperations.contains(DalOperationType.READ_ONE)) {
            item.get(readOneOperation(dalName, tag, entityRef, idClass, problemDetailRef));
        }
        if (exposedOperations.contains(DalOperationType.UPDATE)) {
            item.patch(updateOperation(dalName, tag, entityRef, idClass, problemDetailRef));
        }
        if (exposedOperations.contains(DalOperationType.DELETE)) {
            item.delete(deleteOperation(dalName, tag, idClass, problemDetailRef));
        }

        if (!collection.readOperations().isEmpty()) {
            openApi.getPaths().addPathItem(collectionPath, collection);
        }
        if (!item.readOperations().isEmpty()) {
            openApi.getPaths().addPathItem(itemPath, item);
        }
    }

    private Operation createOperation(
        String dalName, String tag, Schema<?> entityRef, Schema<?> problemDetailRef
    ) {
        return baseOperation(tag, "create_" + dalName, "Create a new " + dalName + " entity")
            .requestBody(jsonRequestBody(entityRef))
            .responses(new ApiResponses()
                .addApiResponse("201", jsonResponse("Entity created", entityRef))
                .addApiResponse("400", problemResponse("Validation failed", problemDetailRef))
                .addApiResponse("403", problemResponse(ACCESS_DENIED_MESSAGE, problemDetailRef))
                .addApiResponse("404", problemResponse("DAL service not found", problemDetailRef))
                .addApiResponse("500", problemResponse(UNEXPECTED_ERROR_MESSAGE, problemDetailRef)));
    }

    private Operation readOperation(
        String dalName, String tag, Class<?> entityClass, Schema<?> entityRef, Schema<?> problemDetailRef
    ) {
        Operation operation =
            baseOperation(tag, "read_" + dalName, "Read a page of " + dalName + " entities");
        operation.addParametersItem(filterDescriber.describe(entityClass));
        pageableParameters().forEach(operation::addParametersItem);
        return operation.responses(new ApiResponses()
            .addApiResponse("200", jsonResponse("Page of entities", pageSchema(entityRef)))
            .addApiResponse("400", problemResponse(MALFORMED_READ_REQUEST_MESSAGE, problemDetailRef))
            .addApiResponse("403", problemResponse(ACCESS_DENIED_MESSAGE, problemDetailRef))
            .addApiResponse("404", problemResponse("DAL service not found", problemDetailRef))
            .addApiResponse("500", problemResponse(UNEXPECTED_ERROR_MESSAGE, problemDetailRef)));
    }

    private Operation readOneOperation(
        String dalName, String tag, Schema<?> entityRef, Class<?> idClass, Schema<?> problemDetailRef
    ) {
        return baseOperation(tag, "readOne_" + dalName, "Read a single " + dalName + " entity by id")
            .addParametersItem(idParameter(idClass))
            .responses(new ApiResponses()
                .addApiResponse("200", jsonResponse("Entity found", entityRef))
                .addApiResponse("403", problemResponse(ACCESS_DENIED_MESSAGE, problemDetailRef))
                .addApiResponse("404", problemResponse(DAL_SERVICE_OR_ENTITY_NOT_FOUND_MESSAGE, problemDetailRef))
                .addApiResponse("500", problemResponse(UNEXPECTED_ERROR_MESSAGE, problemDetailRef)));
    }

    private Operation updateOperation(
        String dalName, String tag, Schema<?> entityRef, Class<?> idClass, Schema<?> problemDetailRef
    ) {
        return baseOperation(tag, "update_" + dalName,
            "Update a " + dalName + " entity by id (RFC 7396 JSON merge patch)")
            .addParametersItem(idParameter(idClass))
            .requestBody(jsonRequestBody(entityRef))
            .responses(new ApiResponses()
                .addApiResponse("200", jsonResponse("Entity updated", entityRef))
                .addApiResponse("204", emptyResponse("Update applied, no content returned"))
                .addApiResponse("400", problemResponse("Validation failed", problemDetailRef))
                .addApiResponse("403", problemResponse(ACCESS_DENIED_MESSAGE, problemDetailRef))
                .addApiResponse("404", problemResponse(DAL_SERVICE_OR_ENTITY_NOT_FOUND_MESSAGE, problemDetailRef))
                .addApiResponse("409", problemResponse(CONCURRENT_MODIFICATION_MESSAGE, problemDetailRef))
                .addApiResponse("500", problemResponse(UNEXPECTED_ERROR_MESSAGE, problemDetailRef)));
    }

    private Operation deleteOperation(String dalName, String tag, Class<?> idClass, Schema<?> problemDetailRef) {
        return baseOperation(tag, "delete_" + dalName, "Delete a " + dalName + " entity by id")
            .addParametersItem(idParameter(idClass))
            .responses(new ApiResponses()
                .addApiResponse("204", emptyResponse("Entity deleted"))
                .addApiResponse("403", problemResponse(ACCESS_DENIED_MESSAGE, problemDetailRef))
                .addApiResponse("404", problemResponse(DAL_SERVICE_OR_ENTITY_NOT_FOUND_MESSAGE, problemDetailRef))
                .addApiResponse("409", problemResponse(CONCURRENT_MODIFICATION_MESSAGE, problemDetailRef))
                .addApiResponse("500", problemResponse(UNEXPECTED_ERROR_MESSAGE, problemDetailRef)));
    }

    private Operation baseOperation(String tag, String operationId, String summary) {
        return new Operation().addTagsItem(tag).operationId(operationId).summary(summary);
    }

    /**
     * Capitalizes the first character of the DAL name for display as the Swagger UI section title, leaving
     * the rest unchanged. The route still uses the original DAL name.
     */
    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private Parameter idParameter(Class<?> idClass) {
        StringSchema schema = new StringSchema();
        String description = "Identifier of the entity.";
        if (TypeUtil.isComplexType(idClass)) {
            String idJson = objectMapper.writeValueAsString(idSkeleton(idClass));
            description += " Composite identifier: a Base64 URL-safe encoded JSON representation of the id"
                + " object, e.g. the encoding of " + idJson + ".";
            schema.example(base64Url(idJson));
        }
        return new Parameter()
            .name(DalRestApiV1.PATH_VARIABLE_ID)
            .in("path")
            .required(true)
            .description(description)
            .schema(schema);
    }

    /**
     * Builds a placeholder JSON skeleton of a composite id ({@code fieldJsonName -> sample value}), used to
     * generate a copy-pasteable Base64 example for the {@code id} path parameter in Swagger UI.
     */
    private Map<String, Object> idSkeleton(Class<?> idClass) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        for (Field field : declaredFields(idClass)) {
            skeleton.put(jsonName(field), placeholder(field.getType()));
        }
        return skeleton;
    }

    private static String jsonName(Field field) {
        JsonProperty annotation = field.getAnnotation(JsonProperty.class);
        return annotation != null && !annotation.value().isEmpty() ? annotation.value() : field.getName();
    }

    private static Object placeholder(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (Number.class.isAssignableFrom(boxed(type))) {
            return 0;
        }
        return "string";
    }

    private static Set<Field> declaredFields(Class<?> type) {
        Set<Field> fields = new LinkedHashSet<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        return type;
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private List<Parameter> pageableParameters() {
        IntegerSchema pageNumberSchema = new IntegerSchema();
        pageNumberSchema.setMinimum(BigDecimal.ZERO);
        pageNumberSchema.setDefault(0);
        Parameter page = new Parameter().name("page").in(Constants.QUERY_PARAM).required(false)
            .description("Zero-based page index (0..N).")
            .schema(pageNumberSchema);

        IntegerSchema pageSizeSchema = new IntegerSchema();
        pageSizeSchema.setMinimum(BigDecimal.ONE);
        pageSizeSchema.setDefault(20);
        Parameter size = new Parameter().name("size").in(Constants.QUERY_PARAM).required(false)
            .description("The size of the page to be returned.")
            .schema(pageSizeSchema);

        Parameter sort = new Parameter().name("sort").in(Constants.QUERY_PARAM).required(false)
            .description("Sorting criteria in the format: property,(asc|desc). Repeat for multiple fields.")
            .schema(new ArraySchema().items(new StringSchema()));
        return List.of(page, size, sort);
    }

    /**
     * Models the {@code PagedModel} DTO Spring Data emits under
     * {@code EnableSpringDataWebSupport(VIA_DTO)}: a {@code content} array plus a {@code page} metadata
     * object.
     */
    private Schema<?> pageSchema(Schema<?> entityRef) {
        ObjectSchema pageMetadata = new ObjectSchema();
        String int64 = "int64";
        pageMetadata.addProperty("size", new IntegerSchema().format(int64));
        pageMetadata.addProperty("number", new IntegerSchema().format(int64));
        pageMetadata.addProperty("totalElements", new IntegerSchema().format(int64));
        pageMetadata.addProperty("totalPages", new IntegerSchema().format(int64));

        ObjectSchema pageSchema = new ObjectSchema();
        pageSchema.addProperty("content", new ArraySchema().items(entityRef));
        pageSchema.addProperty("page", pageMetadata);
        return pageSchema;
    }

    private RequestBody jsonRequestBody(Schema<?> schema) {
        return new RequestBody().required(true).content(jsonContent(schema));
    }

    private ApiResponse jsonResponse(String description, Schema<?> schema) {
        return new ApiResponse().description(description).content(jsonContent(schema));
    }

    /**
     * An RFC 9457 error response: the {@code ProblemDetail} schema served as {@code application/problem+json}.
     */
    private ApiResponse problemResponse(String description, Schema<?> problemDetailRef) {
        Content content = new Content().addMediaType(
            org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            new MediaType().schema(problemDetailRef)
        );
        return new ApiResponse().description(description).content(content);
    }

    private ApiResponse emptyResponse(String description) {
        return new ApiResponse().description(description);
    }

    private Content jsonContent(Schema<?> schema) {
        return new Content().addMediaType(
            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
            new MediaType().schema(schema)
        );
    }
}
