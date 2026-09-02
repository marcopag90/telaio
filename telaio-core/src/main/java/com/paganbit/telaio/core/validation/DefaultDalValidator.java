package com.paganbit.telaio.core.validation;

import com.paganbit.telaio.core.exception.DalEntityValidationException;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.Validator;

import java.util.List;
import java.util.Objects;

/**
 * Default {@link DalValidator}: bean validation through Spring's {@link Validator}, with field
 * errors reported under their JSON wire names.
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public class DefaultDalValidator implements DalValidator {

    private final Validator validator;
    private final JsonPropertyPathResolver pathResolver;

    public DefaultDalValidator(Validator validator, JsonPropertyPathResolver pathResolver) {
        this.validator = Objects.requireNonNull(validator, "Validator must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "JsonPropertyPathResolver must not be null");
    }

    /**
     * Validates the target object as an instance of {@code type}.
     * If validation fails, throws a {@link DalEntityValidationException} containing the validation errors.
     *
     * @param target the object to validate
     * @param type   the entity type the object is validated as
     * @throws DalEntityValidationException if validation fails
     */
    @Override
    public void validate(Object target, Class<?> type) throws DalEntityValidationException {
        final var beanPropertyBindingResult = new BeanPropertyBindingResult(target, type.getSimpleName());
        validator.validate(target, beanPropertyBindingResult);
        if (beanPropertyBindingResult.hasErrors()) {
            List<FieldError> errors = beanPropertyBindingResult.getFieldErrors().stream()
                .map(error -> withJsonFieldName(type, error))
                .toList();
            throw new DalEntityValidationException(errors);
        }
    }

    /**
     * Rewrites a {@link FieldError} so its field name is the JSON name the client uses on the wire,
     * rather than the Java property name reported by bean validation. Honors {@code @JsonProperty} renames
     * and the active {@code PropertyNamingStrategy} (using the deserialization view, matching the input
     * payload). Falls back to the original error unchanged when the path cannot be translated (e.g. an
     * unmapped or indexed path), so error reporting never breaks.
     */
    private FieldError withJsonFieldName(Class<?> type, FieldError error) {
        String javaField = error.getField();
        String jsonField;
        try {
            jsonField = pathResolver.toJsonPath(type, javaField, false);
        } catch (RuntimeException ex) {
            jsonField = null;
        }
        if (jsonField == null || jsonField.equals(javaField)) {
            return error;
        }
        return new FieldError(
            error.getObjectName(),
            jsonField,
            error.getRejectedValue(),
            error.isBindingFailure(),
            error.getCodes(),
            error.getArguments(),
            error.getDefaultMessage()
        );
    }
}
