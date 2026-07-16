package com.paganbit.telaio.rest.contract.v1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationErrorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void serializesToWireShape() {
        ValidationError error = new ValidationError("product", "name", null, "must not be blank");

        String json = objectMapper.writeValueAsString(error);

        assertThat(json)
            .contains("\"object\":\"product\"")
            .contains("\"field\":\"name\"")
            .contains("\"message\":\"must not be blank\"");
    }

    @Test
    void deserializesFromWireShape() {
        String json = """
            {"object":"product","field":"price","rejectValue":-1,"message":"must be positive"}""";

        ValidationError error = objectMapper.readValue(json, ValidationError.class);

        assertThat(error.object()).isEqualTo("product");
        assertThat(error.field()).isEqualTo("price");
        assertThat(error.rejectValue()).isEqualTo(-1);
        assertThat(error.message()).isEqualTo("must be positive");
    }

    @Test
    void toleratesAbsentOptionalMembers() {
        // object and field are always present on the wire; rejectValue and message are optional.
        String json = """
            {"object":"product","field":"name"}""";

        ValidationError error = objectMapper.readValue(json, ValidationError.class);

        assertThat(error.object()).isEqualTo("product");
        assertThat(error.field()).isEqualTo("name");
        assertThat(error.rejectValue()).isNull();
        assertThat(error.message()).isNull();
        assertThat(error).isEqualTo(new ValidationError("product", "name", null, null));
    }
}
