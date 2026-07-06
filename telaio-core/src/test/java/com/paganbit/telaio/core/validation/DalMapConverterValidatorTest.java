package com.paganbit.telaio.core.validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.exception.DalEntityValidationException;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.Validator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalMapConverterValidatorTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Validator validator;

    private DalMapConverterValidator<TestEntity> converterValidator;

    @BeforeEach
    void setUp() {
        converterValidator = new DalMapConverterValidator<>(objectMapper, validator, TestEntity.class);
    }

    @Test
    void convert_shouldUseObjectMapper() {
        // Given
        Map<String, Object> input = new HashMap<>();
        input.put("name", "Test");
        input.put("value", 123);

        TestEntity expectedResult = new TestEntity("Test", 123);
        when(objectMapper.convertValue(input, TestEntity.class)).thenReturn(expectedResult);

        // When
        TestEntity result = converterValidator.convert(input);

        // Then
        assertSame(expectedResult, result, "Should return the object from ObjectMapper");
        verify(objectMapper).convertValue(input, TestEntity.class);
    }

    @Test
    void validate_validObject_shouldNotThrowException() {
        // Given
        TestEntity entity = new TestEntity("Test", 123);

        // Mock validator to not add any errors
        doAnswer(invocation -> {
            // Do nothing - no errors
            return null;
        }).when(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));

        // When & Then
        assertDoesNotThrow(() -> converterValidator.validate(entity));
        verify(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));
    }

    @Test
    void validate_invalidObject_shouldThrowDalValidatorException() {
        // Given
        TestEntity entity = new TestEntity("Invalid", -1);

        // Mock validator to add errors
        doAnswer(invocation -> {
            BeanPropertyBindingResult errors = invocation.getArgument(1);
            errors.addError(new FieldError("object", "property", null, false, null, null, "Test error message"));
            return null;
        }).when(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));

        // When & Then
        DalEntityValidationException exception = assertThrows(DalEntityValidationException.class,
            () -> converterValidator.validate(entity));

        // Verify exception contains expected errors
        List<FieldError> errors = exception.getErrors();
        assertNotNull(errors);
        assertEquals(1, errors.size());
        assertEquals("Test error message", errors.getFirst().getDefaultMessage());

        verify(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));
    }

    @Test
    void validate_invalidRenamedField_shouldReportJsonPropertyName() {
        // A field renamed via @JsonProperty must be reported to the client by its JSON name, not the
        // Java property name returned by bean validation.
        DalMapConverterValidator<RenamedEntity> converter =
            new DalMapConverterValidator<>(JsonMapper.builder().build(), validator, RenamedEntity.class);
        RenamedEntity entity = new RenamedEntity();

        doAnswer(invocation -> {
            BeanPropertyBindingResult errors = invocation.getArgument(1);
            errors.addError(new FieldError(
                "RenamedEntity", "fullName", null, false, null, null, "must not be blank"));
            return null;
        }).when(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));

        DalEntityValidationException exception = assertThrows(DalEntityValidationException.class,
            () -> converter.validate(entity));

        FieldError reported = exception.getErrors().getFirst();
        assertEquals("full_name", reported.getField(), "should report the JSON name, not the Java name");
        assertEquals("must not be blank", reported.getDefaultMessage());
        assertEquals("RenamedEntity", reported.getObjectName());
    }

    @Test
    void getInputType_shouldReturnMapType() {
        // Given
        TypeFactory typeFactory = JsonMapper.builder().build().getTypeFactory();

        // When
        var result = converterValidator.getInputType(typeFactory);

        // Then
        assertEquals(Map.class, result.getRawClass());
    }

    @Test
    void getOutputType_shouldReturnEntityType() {
        // Given
        TypeFactory typeFactory = JsonMapper.builder().build().getTypeFactory();

        // When
        var result = converterValidator.getOutputType(typeFactory);

        // Then
        assertEquals(TestEntity.class, result.getRawClass());
    }

    @Test
    void testConvert_shouldDeserializeNestedMapCorrectly() {
        // Given
        final var converter = new DalMapConverterValidator<>(JsonMapper.builder().build(), validator, UserDto.class);
        final var input = Map.of(
            "name", "John",
            "address", Map.of(
                "street", "Main Street",
                "city", "Rome"
            )
        );

        // when
        UserDto result = converter.convert(input);

        // then
        assertNotNull(result);
        assertEquals("John", result.getName());
        assertNotNull(result.getAddress());
        assertEquals("Main Street", result.getAddress().getStreet());
        assertEquals("Rome", result.getAddress().getCity());
    }

    private record TestEntity(String name, int value) {
    }

    @Getter
    @Setter
    private static class RenamedEntity {
        @JsonProperty("full_name")
        private String fullName;
    }

    @Getter
    @Setter
    private static class UserDto {
        private String name;
        private AddressDto address;
    }

    @Setter
    @Getter
    private static class AddressDto {
        private String street;
        private String city;
    }
}