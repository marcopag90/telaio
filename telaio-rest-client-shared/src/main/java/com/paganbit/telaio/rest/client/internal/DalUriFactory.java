package com.paganbit.telaio.rest.client.internal;

import com.paganbit.telaio.rest.client.DalPageRequest;
import com.paganbit.telaio.rest.client.DalSort;
import com.paganbit.telaio.rest.contract.v1.DalApiV1;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the relative URI templates of the five DAL API v1 operations.
 *
 * <p>Dynamic values (DAL name, identifier segment, filter, paging parameters) are exposed as URI
 * variables of a {@link DalRequestUri}; the HTTP client's {@code UriBuilderFactory} resolves the
 * template against its configured base URL and strictly encodes the expanded values. The
 * {@code uri(String, Map)} contract is identical on {@code RestClient} and {@code WebClient},
 * keeping this factory fully transport-neutral. The identifier variable is expected to be already in its
 * wire representation (see {@code DalIdCodec}).</p>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public final class DalUriFactory {

    private static final String DAL_NAME_MUST_NOT_BE_NULL = "dalName must not be null";
    private static final String DAL_NAME = "dalName";

    /**
     * A relative URI template with its expansion variables, ready for
     * {@code uri(template, variables)}.
     *
     * @param template  the relative URI template (placeholders only, no expanded values)
     * @param variables the values to expand, keyed by placeholder name
     * @since 1.1.0
     */
    public record DalRequestUri(String template, Map<String, Object> variables) {

        public DalRequestUri {
            Objects.requireNonNull(template, "template must not be null");
            Objects.requireNonNull(variables, "variables must not be null");
            variables = Map.copyOf(variables);
        }
    }

    /** Template of the collection resource ({@code /dal/v1/{dalName}}) without query. */
    public DalRequestUri collection(String dalName) {
        Objects.requireNonNull(dalName, DAL_NAME_MUST_NOT_BE_NULL);
        return new DalRequestUri(DalApiV1.BASE_PATH + "/{dalName}", Map.of(DAL_NAME, dalName));
    }

    /** Template of the collection resource with the {@code q}, paging and sort query parameters. */
    public DalRequestUri collection(String dalName, @Nullable String filter, DalPageRequest pageRequest) {
        Objects.requireNonNull(dalName, DAL_NAME_MUST_NOT_BE_NULL);
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(DAL_NAME, dalName);
        StringBuilder query = new StringBuilder();
        if (filter != null && !filter.isBlank()) {
            appendParameter(query, DalApiV1.REQUEST_PARAM_FILTER, "{filter}");
            variables.put("filter", filter);
        }
        if (pageRequest.page() != null) {
            appendParameter(query, DalApiV1.REQUEST_PARAM_PAGE, "{page}");
            variables.put("page", pageRequest.page());
        }
        if (pageRequest.size() != null) {
            appendParameter(query, DalApiV1.REQUEST_PARAM_SIZE, "{size}");
            variables.put("size", pageRequest.size());
        }
        int index = 0;
        for (DalSort sort : pageRequest.sort()) {
            // Property and direction expand separately, so the literal comma of the Spring Data
            // convention survives encoding, while a comma inside a property name does not.
            String propertyVariable = "sortProperty" + index;
            String directionVariable = "sortDirection" + index++;
            appendParameter(query, DalApiV1.REQUEST_PARAM_SORT,
                "{" + propertyVariable + "},{" + directionVariable + "}");
            variables.put(propertyVariable, sort.property());
            variables.put(directionVariable, sort.direction().name().toLowerCase(Locale.ROOT));
        }
        return new DalRequestUri(DalApiV1.BASE_PATH + "/{dalName}" + query, variables);
    }

    /** Template of the entity resource ({@code /dal/v1/{dalName}/{id}}). */
    public DalRequestUri entity(String dalName, String encodedId) {
        Objects.requireNonNull(dalName, DAL_NAME_MUST_NOT_BE_NULL);
        Objects.requireNonNull(encodedId, "encodedId must not be null");
        return new DalRequestUri(DalApiV1.BASE_PATH + "/{dalName}/{id}",
            Map.of(DAL_NAME, dalName, "id", encodedId));
    }

    private static void appendParameter(StringBuilder query, String name, String valueTemplate) {
        query.append(query.isEmpty() ? '?' : '&').append(name).append('=').append(valueTemplate);
    }
}
