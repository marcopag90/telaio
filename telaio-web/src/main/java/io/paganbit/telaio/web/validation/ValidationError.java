package io.paganbit.telaio.web.validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class ValidationError {

    @JsonProperty(value = "object", required = true)
    @Schema(description = "The object instance that failed the validation")
    private String object;

    @JsonProperty(value = "field", required = true)
    @Schema(description = "The field that failed the validation")
    private String field;

    @JsonProperty("rejectValue")
    @Schema(
        description = "The rejected value that caused the validation failure",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private Object rejectValue;

    @JsonProperty("message")
    @Schema(
        description = "The validation error message",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String message;

    public ValidationError() {
        // Default constructor for deserialization
    }

    public ValidationError(
        String object,
        String field,
        @Nullable Object rejectValue,
        @Nullable String message
    ) {
        this.object = object;
        this.field = field;
        this.rejectValue = rejectValue;
        this.message = message;
    }

    public ValidationError(String object, @Nullable String message) {
        this.object = object;
        this.message = message;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Object getRejectValue() {
        return rejectValue;
    }

    public void setRejectValue(Object rejectValue) {
        this.rejectValue = rejectValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ValidationError that)) return false;
        return Objects.equals(object, that.object) &&
            Objects.equals(field, that.field) &&
            Objects.equals(rejectValue, that.rejectValue) &&
            Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(object, field, rejectValue, message);
    }
}
