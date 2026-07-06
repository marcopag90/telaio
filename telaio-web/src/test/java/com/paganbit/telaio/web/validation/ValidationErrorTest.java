package com.paganbit.telaio.web.validation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class ValidationErrorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void testFullConstructorAndGetters() {
        ValidationError error = new ValidationError("User", "email", "invalid@", "Invalid email format");

        assertEquals("User", error.getObject());
        assertEquals("email", error.getField());
        assertEquals("invalid@", error.getRejectValue());
        assertEquals("Invalid email format", error.getMessage());
    }

    @Test
    void testPartialConstructorAndGetters() {
        ValidationError error = new ValidationError("User", "Generic error");

        assertEquals("User", error.getObject());
        assertNull(error.getField());
        assertNull(error.getRejectValue());
        assertEquals("Generic error", error.getMessage());
    }

    @Test
    void testSetters() {
        ValidationError error = new ValidationError("User", null);

        error.setField("username");
        error.setRejectValue("!nv@lid");
        error.setMessage("Invalid username");

        assertEquals("username", error.getField());
        assertEquals("!nv@lid", error.getRejectValue());
        assertEquals("Invalid username", error.getMessage());
    }

    @Test
    void testEqualsAndHashCode() {
        ValidationError error1 = new ValidationError("User", "password", "123", "Too weak");
        ValidationError error2 = new ValidationError("User", "password", "123", "Too weak");

        assertEquals(error1, error2);
        assertEquals(error1.hashCode(), error2.hashCode());

        ValidationError error3 = new ValidationError("Admin", "password", "123", "Too weak");
        assertNotEquals(error1, error3);
    }

    @Test
    void testJsonSerialization() {
        ValidationError error = new ValidationError("User", "email", "invalid@", "Invalid email format");

        String json = objectMapper.writeValueAsString(error);

        assertTrue(json.contains("\"object\":\"User\""));
        assertTrue(json.contains("\"field\":\"email\""));
        assertTrue(json.contains("\"rejectValue\":\"invalid@\""));
        assertTrue(json.contains("\"message\":\"Invalid email format\""));
    }

    @Test
    void testJsonDeserialization() {
        String json = """
            {
              "object": "User",
              "field": "email",
              "rejectValue": "invalid@",
              "message": "Invalid email format"
            }
            """;

        ValidationError error = objectMapper.readValue(json, ValidationError.class);

        assertEquals("User", error.getObject());
        assertEquals("email", error.getField());
        assertEquals("invalid@", error.getRejectValue());
        assertEquals("Invalid email format", error.getMessage());
    }
}
