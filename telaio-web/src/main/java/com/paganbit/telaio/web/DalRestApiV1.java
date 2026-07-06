package com.paganbit.telaio.web;

import com.paganbit.telaio.web.annotation.DalId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API contract for DAL services — version 1.
 *
 * <p>Defines HTTP endpoint signatures for create, read, readOne, update, and delete operations
 * on dynamic DAL services, identified by name.
 * Also serves as the single source of API path constants.</p>
 *
 * <p>Integrates OpenAPI annotations for automatic documentation.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@RequestMapping(DalRestApiV1.BASE_PATH)
@Tag(name = "DAL API v1", description = "Restful endpoints for DAL services")
public interface DalRestApiV1 {

    String BASE_PATH = "/dal/v1";
    String PATH_VARIABLE_DAL_NAME = "dalName";
    String PATH_VARIABLE_ID = "id";
    String REQUEST_PARAM_FILTER = "q";

    @PostMapping(
        value = "{" + PATH_VARIABLE_DAL_NAME + "}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new object in the specified DAL")
    @ApiResponse(responseCode = "201", description = "Entity created")
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
        responseCode = "403",
        description = "Access denied",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "DAL service not found", content = @Content)
    Object create(@PathVariable String dalName, @RequestBody Map<String, Object> input);

    @GetMapping(
        value = "{" + PATH_VARIABLE_DAL_NAME + "}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Read a page of objects from the specified DAL")
    @ApiResponse(responseCode = "200", description = "Page of entities")
    @ApiResponse(
        responseCode = "403",
        description = "Access denied",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "DAL service not found", content = @Content)
    Page<Object> read(
        @PathVariable String dalName,
        @RequestParam(value = REQUEST_PARAM_FILTER, required = false) String filter,
        @ParameterObject Pageable pageable
    );

    @GetMapping(
        value = "{" + PATH_VARIABLE_DAL_NAME + "}/{" + PATH_VARIABLE_ID + "}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Read a single object from the specified DAL by ID")
    @ApiResponse(responseCode = "200", description = "Entity found")
    @ApiResponse(
        responseCode = "403",
        description = "Access denied",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "DAL service or entity not found", content = @Content)
    Object readOne(
        @PathVariable String dalName,
        @Parameter(name = PATH_VARIABLE_ID, schema = @Schema(type = "string"), in = ParameterIn.PATH)
        @DalId Object id
    );

    @PatchMapping(
        value = "{" + PATH_VARIABLE_DAL_NAME + "}/{" + PATH_VARIABLE_ID + "}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Update an object in the specified DAL by ID")
    @ApiResponse(responseCode = "200", description = "Entity updated")
    @ApiResponse(responseCode = "204", description = "Update applied, no content returned")
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
        responseCode = "403",
        description = "Access denied",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "DAL service or entity not found", content = @Content)
    @ApiResponse(
        responseCode = "409",
        description = "Concurrent modification of a versioned entity — re-read and retry",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Object> update(
        @PathVariable String dalName,
        @Parameter(name = PATH_VARIABLE_ID, schema = @Schema(type = "string"), in = ParameterIn.PATH)
        @DalId Object id,
        @RequestBody Map<String, Object> patch
    );

    @DeleteMapping(value = "{" + PATH_VARIABLE_DAL_NAME + "}/{" + PATH_VARIABLE_ID + "}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an object in the specified DAL by ID")
    @ApiResponse(responseCode = "204", description = "Entity deleted")
    @ApiResponse(
        responseCode = "403",
        description = "Access denied",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "DAL service or entity not found", content = @Content)
    @ApiResponse(
        responseCode = "409",
        description = "Concurrent modification of a versioned entity — re-read and retry",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    void delete(
        @PathVariable String dalName,
        @Parameter(name = PATH_VARIABLE_ID, schema = @Schema(type = "string"), in = ParameterIn.PATH)
        @DalId Object id
    );
}
