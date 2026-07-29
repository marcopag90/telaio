package com.paganbit.telaio.rest.contract.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * A single field-level validation failure, carried in the {@link DalApiV1#PROBLEM_PROPERTY_ERRORS}
 * extension property of a {@code 400 Bad Request} problem response.
 *
 * @param object      the object instance that failed the validation
 * @param field       the field that failed the validation
 * @param rejectValue the rejected value that caused the failure, or {@code null}
 * @param message     the validation error message, or {@code null}
 * @author Marco Pagan
 * @since 1.1.0
 */
public record ValidationError(

    @JsonProperty(value = "object", required = true)
    @Schema(description = "The object instance that failed the validation")
    String object,

    @JsonProperty(value = "field", required = true)
    @Schema(description = "The field that failed the validation")
    String field,

    @JsonProperty("rejectValue")
    @Schema(
        description = "The rejected value that caused the validation failure",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Nullable Object rejectValue,

    @JsonProperty("message")
    @Schema(description = "The validation error message", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Nullable String message
) {
}
