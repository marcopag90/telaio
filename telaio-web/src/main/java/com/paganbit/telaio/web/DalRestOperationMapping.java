package com.paganbit.telaio.web;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.rest.contract.v1.DalApiV1;
import org.springframework.http.HttpMethod;

import java.util.*;

/**
 * The one place that maps DAL operations onto the {@code /dal/v1} REST surface: which URI serves each
 * operation and with which HTTP method.
 *
 * <p>Two URIs exist per DAL — the collection ({@code /dal/v1/{dalName}}) serving {@code CREATE} and
 * {@code READ}, and the item ({@code /dal/v1/{dalName}/{id}}) serving {@code READ_ONE}, {@code UPDATE}
 * and {@code DELETE}. The controller contract, the {@code Allow} header of a {@code 405} response and the
 * OpenAPI generator all read the mapping from here, so they cannot drift apart.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public final class DalRestOperationMapping {

    /**
     * The collection URI template, relative to {@link DalApiV1#BASE_PATH}.
     */
    public static final String COLLECTION_PATH = "{" + DalApiV1.PATH_VARIABLE_DAL_NAME + "}";

    /**
     * The item URI template, relative to {@link DalApiV1#BASE_PATH}.
     */
    public static final String ITEM_PATH = COLLECTION_PATH + "/{" + DalApiV1.PATH_VARIABLE_ID + "}";

    /**
     * Operations served by the collection URI.
     */
    public static final Set<DalOperationType> COLLECTION_OPERATIONS =
        Collections.unmodifiableSet(EnumSet.of(DalOperationType.CREATE, DalOperationType.READ));

    /**
     * Operations served by the item URI.
     */
    public static final Set<DalOperationType> ITEM_OPERATIONS = Collections.unmodifiableSet(
        EnumSet.of(DalOperationType.READ_ONE, DalOperationType.UPDATE, DalOperationType.DELETE));

    private static final Map<DalOperationType, HttpMethod> HTTP_METHODS = new EnumMap<>(Map.of(
        DalOperationType.CREATE, HttpMethod.POST,
        DalOperationType.READ, HttpMethod.GET,
        DalOperationType.READ_ONE, HttpMethod.GET,
        DalOperationType.UPDATE, HttpMethod.PATCH,
        DalOperationType.DELETE, HttpMethod.DELETE
    ));

    private DalRestOperationMapping() {
    }

    /**
     * Returns the absolute collection path of a DAL, e.g. {@code /dal/v1/products}. Passing
     * {@link #COLLECTION_PATH} gives the templated form {@code /dal/v1/{dalName}}.
     *
     * @param dalName the DAL name, or a path template
     */
    public static String collectionPath(String dalName) {
        return DalApiV1.BASE_PATH + "/" + dalName;
    }

    /**
     * Returns the absolute item path template of a DAL, e.g. {@code /dal/v1/products/{id}}. Passing
     * {@link #COLLECTION_PATH} gives the fully templated form {@code /dal/v1/{dalName}/{id}}.
     *
     * @param dalName the DAL name, or a path template
     */
    public static String itemPath(String dalName) {
        return collectionPath(dalName) + "/{" + DalApiV1.PATH_VARIABLE_ID + "}";
    }

    /**
     * Returns the HTTP method serving the given operation.
     */
    public static HttpMethod httpMethod(DalOperationType operation) {
        return Objects.requireNonNull(HTTP_METHODS.get(operation));
    }

    /**
     * Returns the operations sharing the URI of the given one, the operation itself included.
     */
    public static Set<DalOperationType> uriGroup(DalOperationType operation) {
        return COLLECTION_OPERATIONS.contains(operation) ? COLLECTION_OPERATIONS : ITEM_OPERATIONS;
    }

    /**
     * Returns the HTTP methods still served on the URI of {@code attempted}, given the operations a DAL
     * exposes: the {@code Allow} header of a {@code 405} response. Empty when the URI serves nothing,
     * which is the {@code 404} case.
     *
     * @param exposed   the operations the DAL exposes on REST
     * @param attempted the operation that was attempted and is not exposed
     * @return the allowed methods, in operation order, without duplicates
     */
    public static Set<HttpMethod> allowedMethods(Set<DalOperationType> exposed, DalOperationType attempted) {
        Set<HttpMethod> allowed = new LinkedHashSet<>();
        for (DalOperationType sibling : uriGroup(attempted)) {
            if (exposed.contains(sibling)) {
                allowed.add(httpMethod(sibling));
            }
        }
        return allowed;
    }
}
