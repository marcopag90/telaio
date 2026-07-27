package com.paganbit.telaio.rest.contract.v1;

/**
 * Constants of the DAL REST API v1 wire contract.
 *
 * <p>Single source of truth for the {@code /dal/v1} paths, parameter names and
 * {@code ProblemDetail} extension properties, shared by the server-side controller contract
 * ({@code com.paganbit.telaio.web.DalRestApiV1}) and by remote clients.</p>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public final class DalApiV1 {

    /**
     * Base path of the DAL REST API v1.
     */
    public static final String BASE_PATH = "/dal/v1";

    /**
     * Path variable holding the DAL service name.
     */
    public static final String PATH_VARIABLE_DAL_NAME = "dalName";

    /**
     * Path variable holding the entity ID.
     */
    public static final String PATH_VARIABLE_ID = "id";

    /**
     * Query parameter carrying the filter expression.
     */
    public static final String REQUEST_PARAM_FILTER = "q";

    /**
     * Query parameter carrying the zero-based page index (Spring Data web convention).
     */
    public static final String REQUEST_PARAM_PAGE = "page";

    /**
     * Query parameter carrying the page size (Spring Data web convention).
     */
    public static final String REQUEST_PARAM_SIZE = "size";

    /**
     * Repeatable query parameter carrying {@code property,direction} sort orders.
     */
    public static final String REQUEST_PARAM_SORT = "sort";

    /**
     * {@code ProblemDetail} extension property carrying the array of {@link ValidationError}
     * entries on {@code 400 Bad Request} validation failures.
     */
    public static final String PROBLEM_PROPERTY_ERRORS = "errors";

    private DalApiV1() {
        //noop
    }
}
