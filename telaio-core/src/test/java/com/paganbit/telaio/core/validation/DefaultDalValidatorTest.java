package com.paganbit.telaio.core.validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.exception.DalEntityValidationException;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.Validator;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultDalValidatorTest {

    @Mock
    private Validator validator;

    private DefaultDalValidator dalValidator(Validator validator) {
        return new DefaultDalValidator(
            validator, new JsonPropertyPathResolver(JsonMapper.builder().build()));
    }

    @Test
    void validate_validObject_shouldNotThrow() {
        TestEntity entity = new TestEntity();

        DefaultDalValidator target = dalValidator(validator);
        assertThatCode(() -> target.validate(entity, TestEntity.class)).doesNotThrowAnyException();
        verify(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));
    }

    @Test
    void validate_invalidObject_shouldThrowWithTheCollectedErrors() {
        TestEntity entity = new TestEntity();
        doAnswer(invocation -> {
            BeanPropertyBindingResult errors = invocation.getArgument(1);
            errors.addError(new FieldError("object", "property", null, false, null, null, "Test error message"));
            return null;
        }).when(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));
        DefaultDalValidator target = dalValidator(validator);

        assertThatThrownBy(() -> target.validate(entity, TestEntity.class))
            .isInstanceOfSatisfying(DalEntityValidationException.class, exception -> {
                List<FieldError> errors = exception.getErrors();
                assertThat(errors).hasSize(1);
                assertThat(errors.getFirst().getDefaultMessage()).isEqualTo("Test error message");
            });
    }

    @Test
    void validate_reportsTheObjectNameFromTheDeclaredType() {
        // The declared type wins over the instance's runtime class: the instance at hand may be a
        // proxy or an anonymous subclass, whose class name would leak into the report.
        TestEntity entity = new TestEntity() {
        };
        doAnswer(invocation -> {
            BeanPropertyBindingResult errors = invocation.getArgument(1);
            assertThat(errors.getObjectName()).isEqualTo("TestEntity");
            errors.addError(new FieldError(
                errors.getObjectName(), "name", null, false, null, null, "must not be blank"));
            return null;
        }).when(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));
        DefaultDalValidator target = dalValidator(validator);

        assertThatThrownBy(() -> target.validate(entity, TestEntity.class))
            .isInstanceOfSatisfying(DalEntityValidationException.class, exception ->
                assertThat(exception.getErrors().getFirst().getObjectName()).isEqualTo("TestEntity"));
    }

    @Test
    void validate_invalidRenamedField_shouldReportJsonPropertyName() {
        // A field renamed via @JsonProperty must be reported to the client by its JSON name, not the
        // Java property name returned by bean validation.
        RenamedEntity entity = new RenamedEntity();
        doAnswer(invocation -> {
            BeanPropertyBindingResult errors = invocation.getArgument(1);
            errors.addError(new FieldError(
                "RenamedEntity", "fullName", null, false, null, null, "must not be blank"));
            return null;
        }).when(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));
        DefaultDalValidator target = dalValidator(validator);

        assertThatThrownBy(() -> target.validate(entity, RenamedEntity.class))
            .isInstanceOfSatisfying(DalEntityValidationException.class, exception -> {
                FieldError reported = exception.getErrors().getFirst();
                assertThat(reported.getField())
                    .as("should report the JSON name, not the Java name")
                    .isEqualTo("full_name");
                assertThat(reported.getDefaultMessage()).isEqualTo("must not be blank");
                assertThat(reported.getObjectName()).isEqualTo("RenamedEntity");
            });
    }

    @Test
    void validate_unresolvableFieldPath_shouldKeepTheOriginalError() {
        // A path the resolver cannot translate (e.g. an indexed segment) must fall through unchanged:
        // error reporting never breaks.
        TestEntity entity = new TestEntity();
        doAnswer(invocation -> {
            BeanPropertyBindingResult errors = invocation.getArgument(1);
            errors.addError(new FieldError(
                "TestEntity", "items[0].nope", null, false, null, null, "invalid"));
            return null;
        }).when(validator).validate(eq(entity), any(BeanPropertyBindingResult.class));
        DefaultDalValidator target = dalValidator(validator);

        assertThatThrownBy(() -> target.validate(entity, TestEntity.class))
            .isInstanceOfSatisfying(DalEntityValidationException.class, exception ->
                assertThat(exception.getErrors().getFirst().getField()).isEqualTo("items[0].nope"));
    }

    @Test
    void constructor_rejectsNullCollaborators() {
        JsonPropertyPathResolver pathResolver = new JsonPropertyPathResolver(JsonMapper.builder().build());
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultDalValidator(null, pathResolver))
            .withMessageContaining("Validator");
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultDalValidator(validator, null))
            .withMessageContaining("JsonPropertyPathResolver");
    }

    @Getter
    @Setter
    static class TestEntity {

        private String name;
    }

    @Getter
    @Setter
    static class RenamedEntity {

        @JsonProperty("full_name")
        private String fullName;
    }
}
