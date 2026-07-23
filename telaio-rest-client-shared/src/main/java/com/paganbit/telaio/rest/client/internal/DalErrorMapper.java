package com.paganbit.telaio.rest.client.internal;

import com.paganbit.telaio.rest.client.exception.*;
import com.paganbit.telaio.rest.contract.v1.DalApiV1;
import com.paganbit.telaio.rest.contract.v1.ValidationError;
import org.jspecify.annotations.Nullable;
import org.springframework.http.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps a non-2xx response of the DAL API v1 (status, headers and fully-read body bytes — no
 * blocking or reactive I/O types) onto the client exception hierarchy:
 * <ul>
 *   <li>{@code 400} with the {@code errors} problem extension → validation failure; without it
 *       → bad request</li>
 *   <li>{@code 403} → forbidden</li>
 *   <li>{@code 404} with a problem body → not found; bodiless → operation not exposed</li>
 *   <li>{@code 405} (bodiless, with {@code Allow}) → operation not exposed</li>
 *   <li>{@code 409} → optimistic-lock conflict</li>
 *   <li>{@code 5xx} → server failure; anything else → generic response exception</li>
 * </ul>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public final class DalErrorMapper {

    private final ObjectMapper objectMapper;

    public DalErrorMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Maps an error response onto the exception to throw.
     *
     * @param status  the response status
     * @param headers the response headers
     * @param body    the fully-read response body, or {@code null}/empty when absent
     * @return the mapped exception (never returns normally at call sites — callers throw it)
     */
    public DalClientException map(HttpStatusCode status, HttpHeaders headers, byte @Nullable [] body) {
        JsonNode problemNode = readProblemNode(headers, body);
        ProblemDetail problem = problemNode == null ? null : toProblemDetail(status, problemNode);

        int code = status.value();
        if (code == HttpStatus.BAD_REQUEST.value()) {
            List<ValidationError> errors = extractValidationErrors(problemNode);
            if (errors != null) {
                return new DalClientValidationException(
                    detailOr(problem, "Validation failed"), code, problem, errors);
            }
            return new DalClientBadRequestException(detailOr(problem, "Bad request"), code, problem);
        }
        if (code == HttpStatus.FORBIDDEN.value()) {
            return new DalClientForbiddenException("Access denied", code, problem);
        }
        if (code == HttpStatus.NOT_FOUND.value()) {
            if (problem != null) {
                return new DalClientNotFoundException(
                    detailOr(problem, "Resource not found"), code, problem);
            }
            if (body == null || body.length == 0) {
                // Deliberately bodyless on the server: the URI exposes no operation at all.
                return new DalClientOperationNotExposedException(
                    "The target URI exposes no operation", code, java.util.Set.of());
            }
            // A non-empty, non-problem 404 body (e.g. a gateway/proxy error page) is an
            // infrastructure condition, not the server's not-exposed signal.
            return new DalClientResponseException("Unexpected 404 response", code, null);
        }
        if (code == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return new DalClientOperationNotExposedException(
                "The attempted operation is not exposed by the target DAL; allowed: "
                    + headers.getAllow(), code, headers.getAllow());
        }
        if (code == HttpStatus.CONFLICT.value()) {
            return new DalClientConflictException(
                detailOr(problem, "The resource was modified concurrently; re-read and retry"),
                code, problem);
        }
        if (status.is5xxServerError()) {
            return new DalClientServerException(
                detailOr(problem, "The server failed to process the request"), code, problem);
        }
        return new DalClientResponseException("Unexpected response status " + code, code, problem);
    }

    private @Nullable JsonNode readProblemNode(HttpHeaders headers, byte @Nullable [] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        MediaType contentType = headers.getContentType();
        if (contentType == null || !MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.isObject() ? node : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private ProblemDetail toProblemDetail(HttpStatusCode status, JsonNode node) {
        ProblemDetail problem = ProblemDetail.forStatus(status.value());
        // Hardened member by member: a hostile or broken server must never make the mapper
        // throw anything but the mapped DalClientException — unparseable members are skipped.
        node.properties().forEach(property -> {
            String name = property.getKey();
            JsonNode value = property.getValue();
            try {
                switch (name) {
                    case "type" -> problem.setType(URI.create(value.asString()));
                    case "title" -> problem.setTitle(value.asString());
                    case "status" -> { /* already carried by the response status */ }
                    case "detail" -> problem.setDetail(value.asString());
                    case "instance" -> problem.setInstance(URI.create(value.asString()));
                    default -> problem.setProperty(name, objectMapper.treeToValue(value, Object.class));
                }
            } catch (RuntimeException e) {
                // Malformed member (e.g. an invalid URI in "type"): omit it, keep the rest.
            }
        });
        return problem;
    }

    private @Nullable List<ValidationError> extractValidationErrors(@Nullable JsonNode problemNode) {
        if (problemNode == null) {
            return null;
        }
        JsonNode errorsNode = problemNode.path(DalApiV1.PROBLEM_PROPERTY_ERRORS);
        if (!errorsNode.isArray()) {
            return null;
        }
        List<ValidationError> errors = new ArrayList<>();
        for (JsonNode element : errorsNode) {
            try {
                errors.add(objectMapper.treeToValue(element, ValidationError.class));
            } catch (RuntimeException e) {
                // Skip the unparseable entry; the remaining errors still reach the caller.
            }
        }
        return errors;
    }

    private static String detailOr(@Nullable ProblemDetail problem, String fallback) {
        if (problem == null) {
            return fallback;
        }
        String detail = problem.getDetail();
        return detail != null ? detail : fallback;
    }
}
