package io.paganbit.telaio.core.validation;

import io.paganbit.telaio.core.exception.DalEntityValidationException;
import io.paganbit.telaio.core.json.JsonPropertyPathResolver;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.Validator;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.util.Converter;

import java.util.List;
import java.util.Map;

/**
 * A combined converter and validator for DAL.
 * This class provides functionality to:
 * <ul>
 *   <li>Convert from a Map to a target type using Jackson's ObjectMapper</li>
 *   <li>Validate the converted object using Spring's Validator</li>
 * </ul>
 *
 * @param <T> the target type to convert to and validate
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalMapConverterValidator<T> implements Converter<Map<String, Object>, T>, DalValidator<T> {

    protected final Class<T> converterType;
    protected ObjectMapper objectMapper;
    protected Validator validator;
    protected JsonPropertyPathResolver pathResolver;

    public DalMapConverterValidator(
        ObjectMapper objectMapper,
        Validator validator,
        Class<T> converterType
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.converterType = converterType;
        this.pathResolver = new JsonPropertyPathResolver(objectMapper);
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.pathResolver = new JsonPropertyPathResolver(objectMapper);
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * Validates the target object using Spring's Validator.
     * If validation fails, throws a {@link DalEntityValidationException} containing the validation errors.
     *
     * @param target the object to validate
     * @throws DalEntityValidationException if validation fails
     */
    @Override
    public void validate(T target) throws DalEntityValidationException {
        final var beanPropertyBindingResult = new BeanPropertyBindingResult(target, converterType.getSimpleName());
        validator.validate(target, beanPropertyBindingResult);
        if (beanPropertyBindingResult.hasErrors()) {
            List<FieldError> errors = beanPropertyBindingResult.getFieldErrors().stream()
                .map(this::withJsonFieldName)
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
    private FieldError withJsonFieldName(FieldError error) {
        String javaField = error.getField();
        String jsonField;
        try {
            jsonField = pathResolver.toJsonPath(converterType, javaField, false);
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

    /**
     * Returns the input type for the converter.
     *
     * @param typeFactory the TypeFactory to use
     * @return the JavaType representing Map.class
     */
    @Override
    public JavaType getInputType(TypeFactory typeFactory) {
        return typeFactory.constructType(Map.class);
    }

    /**
     * Returns the output type for the converter.
     *
     * @param typeFactory the TypeFactory to use
     * @return the JavaType representing the target type
     */
    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {
        return typeFactory.constructType(converterType);
    }

    /**
     * Converts a Map to the target type using Jackson's ObjectMapper.
     *
     * @param input the Map to convert
     * @return the converted object of type T
     */
    @Override
    public T convert(SerializationContext ctx, Map<String, Object> input) {
        return convert(input);
    }

    @Override
    public T convert(DeserializationContext ctx, Map<String, Object> input) {
        return convert(input);
    }

    public T convert(Map<String, Object> input) {
        return objectMapper.convertValue(input, converterType);
    }
}