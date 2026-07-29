package com.paganbit.telaio.rest.client.internal;

import com.paganbit.telaio.rest.client.exception.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.*;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DalErrorMapperTest {

    /** Literal snapshot of the server's 400-validation wire shape (see TelaioWebExceptionHandler). */
    private static final String VALIDATION_PROBLEM = """
        {
          "type": "about:blank",
          "title": "Bad Request",
          "status": 400,
          "detail": "Validation failed",
          "errors": [
            {"object": "product", "field": "name", "rejectValue": null, "message": "must not be blank"},
            {"object": "product", "field": "price", "rejectValue": -1, "message": "must be positive"}
          ]
        }""";

    private final DalErrorMapper mapper = new DalErrorMapper(JsonMapper.builder().build());

    private DalClientException map(HttpStatus status, @Nullable String problemJson) {
        return map(status, problemJson, new HttpHeaders());
    }

    private DalClientException map(HttpStatus status, @Nullable String problemJson, HttpHeaders headers) {
        byte[] body = null;
        if (problemJson != null) {
            headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            body = problemJson.getBytes(StandardCharsets.UTF_8);
        }
        return mapper.map(status, headers, body);
    }

    @Test
    void badRequestWithErrorsPropertyIsValidationFailure() {
        DalClientException ex = map(HttpStatus.BAD_REQUEST, VALIDATION_PROBLEM);

        assertThat(ex).isInstanceOf(DalClientValidationException.class);
        DalClientValidationException validation = (DalClientValidationException) ex;
        assertThat(validation.statusCode()).isEqualTo(400);
        assertThat(validation.getMessage()).isEqualTo("Validation failed");
        assertThat(validation.errors()).hasSize(2);
        assertThat(validation.errors().getFirst().field()).isEqualTo("name");
        assertThat(validation.errors().get(1).rejectValue()).isEqualTo(-1);
        assertThat(validation.problemDetail()).isNotNull();
    }

    @Test
    void badRequestWithoutErrorsPropertyIsBadRequest() {
        DalClientException ex = map(HttpStatus.BAD_REQUEST, """
            {"status": 400, "detail": "Malformed filter expression"}""");

        assertThat(ex).isInstanceOf(DalClientBadRequestException.class);
        assertThat(ex.getMessage()).isEqualTo("Malformed filter expression");
    }

    @Test
    void forbiddenIsMappedWithBareProblem() {
        DalClientException ex = map(HttpStatus.FORBIDDEN, """
            {"type": "about:blank", "title": "Forbidden", "status": 403}""");

        assertThat(ex).isInstanceOf(DalClientForbiddenException.class);
        DalClientForbiddenException forbidden = (DalClientForbiddenException) ex;
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.problemDetail()).isNotNull();
        assertThat(forbidden.problemDetail().getDetail()).isNull();
    }

    @Test
    void notFoundWithProblemBodyIsNotFound() {
        DalClientException ex = map(HttpStatus.NOT_FOUND, """
            {"status": 404, "detail": "Resource not found with ID: 5"}""");

        assertThat(ex).isInstanceOf(DalClientNotFoundException.class);
        assertThat(ex.getMessage()).isEqualTo("Resource not found with ID: 5");
    }

    @Test
    void bodylessNotFoundIsOperationNotExposed() {
        DalClientException ex = map(HttpStatus.NOT_FOUND, null);

        assertThat(ex).isInstanceOf(DalClientOperationNotExposedException.class);
        assertThat(((DalClientOperationNotExposedException) ex).allowedMethods()).isEmpty();
    }

    @Test
    void methodNotAllowedCarriesAllowHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(java.util.Set.of(HttpMethod.GET, HttpMethod.DELETE));

        DalClientException ex = map(HttpStatus.METHOD_NOT_ALLOWED, null, headers);

        assertThat(ex).isInstanceOf(DalClientOperationNotExposedException.class);
        assertThat(((DalClientOperationNotExposedException) ex).allowedMethods())
            .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    void conflictIsMapped() {
        DalClientException ex = map(HttpStatus.CONFLICT, """
            {"status": 409, "detail": "The resource was modified concurrently; re-read and retry"}""");

        assertThat(ex).isInstanceOf(DalClientConflictException.class);
    }

    @Test
    void serverErrorIsMapped() {
        DalClientException ex = map(HttpStatus.INTERNAL_SERVER_ERROR, """
            {"status": 500, "detail": "An unexpected error occurred"}""");

        assertThat(ex).isInstanceOf(DalClientServerException.class);
        assertThat(((DalClientServerException) ex).statusCode()).isEqualTo(500);
    }

    @Test
    void nonProblem404BodyIsAnInfrastructureConditionNotNotExposed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);

        DalClientException ex = mapper.map(HttpStatus.NOT_FOUND, headers,
            "<html>gateway error page</html>".getBytes(StandardCharsets.UTF_8));

        // A non-empty, non-problem 404 (e.g. a proxy error page) must not masquerade as the
        // server's deliberate bodyless not-exposed signal.
        assertThat(ex).isExactlyInstanceOf(DalClientResponseException.class);
        assertThat(((DalClientResponseException) ex).statusCode()).isEqualTo(404);
    }

    @Test
    void unexpectedStatusFallsBackToResponseException() {
        DalClientException ex = map(HttpStatus.PAYMENT_REQUIRED, null);

        assertThat(ex).isExactlyInstanceOf(DalClientResponseException.class);
        assertThat(((DalClientResponseException) ex).statusCode()).isEqualTo(402);
    }

    @Test
    void malformedProblemJsonIsToleratedAsBodiless() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        DalClientException ex = mapper.map(HttpStatus.CONFLICT, headers,
            "{broken".getBytes(StandardCharsets.UTF_8));

        assertThat(ex).isInstanceOf(DalClientConflictException.class);
        assertThat(((DalClientConflictException) ex).problemDetail()).isNull();
    }

    @Test
    void bodyWithoutContentTypeIsNotParsedAsProblem() {
        HttpHeaders headers = new HttpHeaders();

        DalClientException ex = mapper.map(HttpStatus.BAD_REQUEST, headers,
            "{\"detail\": \"ignored\"}".getBytes(StandardCharsets.UTF_8));

        // No Content-Type → the body is not read as a problem; the generic fallback message wins.
        assertThat(ex).isInstanceOf(DalClientBadRequestException.class);
        assertThat(ex.getMessage()).isEqualTo("Bad request");
        assertThat(((DalClientBadRequestException) ex).problemDetail()).isNull();
    }

    @Test
    void nonObjectProblemBodyIsIgnored() {
        // A problem+json body that is a JSON array (not an object) carries no members to map.
        DalClientException ex = map(HttpStatus.BAD_REQUEST, "[1, 2, 3]");

        assertThat(ex).isInstanceOf(DalClientBadRequestException.class);
        assertThat(ex.getMessage()).isEqualTo("Bad request");
    }

    @Test
    void malformedTypeUriIsSkippedButOtherMembersSurvive() {
        // URI.create on ":::" throws — the type member is dropped, the rest of the problem stays.
        DalClientException ex = map(HttpStatus.BAD_REQUEST, """
            {"type": ":::", "title": "Bad Request", "detail": "Malformed filter", "instance": "/dal/v1/products"}""");

        assertThat(ex).isInstanceOf(DalClientBadRequestException.class);
        assertThat(ex.getMessage()).isEqualTo("Malformed filter");
        ProblemDetail problem = ((DalClientBadRequestException) ex).problemDetail();
        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getInstance()).isEqualTo(java.net.URI.create("/dal/v1/products"));
        // The malformed type was skipped, leaving the member at its unset default.
        assertThat(problem.getType()).isNull();
    }

    @Test
    void customProblemExtensionMemberIsCarriedThrough() {
        DalClientException ex = map(HttpStatus.CONFLICT, """
            {"status": 409, "detail": "conflict", "traceId": "abc-123"}""");

        ProblemDetail problem = ((DalClientConflictException) ex).problemDetail();
        assertThat(problem).isNotNull();
        assertThat(problem.getProperties()).containsEntry("traceId", "abc-123");
    }

    @Test
    void unparseableValidationEntryIsSkippedAndOthersSurvive() {
        DalClientException ex = map(HttpStatus.BAD_REQUEST, """
            {
              "status": 400,
              "detail": "Validation failed",
              "errors": [
                "not-an-object",
                {"object": "product", "field": "name", "rejectValue": null, "message": "must not be blank"}
              ]
            }""");

        assertThat(ex).isInstanceOf(DalClientValidationException.class);
        DalClientValidationException validation = (DalClientValidationException) ex;
        assertThat(validation.errors()).hasSize(1);
        assertThat(validation.errors().getFirst().field()).isEqualTo("name");
    }

    @Test
    void notFoundProblemWithoutDetailFallsBackToDefaultMessage() {
        DalClientException ex = map(HttpStatus.NOT_FOUND, """
            {"status": 404, "title": "Not Found"}""");

        assertThat(ex).isInstanceOf(DalClientNotFoundException.class);
        assertThat(ex.getMessage()).isEqualTo("Resource not found");
    }
}
