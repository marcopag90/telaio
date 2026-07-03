package io.paganbit.telaio.core.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonPropertyPathResolverTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final JsonPropertyPathResolver resolver = new JsonPropertyPathResolver(objectMapper);

    @Test
    void translatesJsonPropertyRename() {
        assertEquals("full_name", resolver.toJsonPath(Person.class, "fullName", false));
        assertEquals("full_name", resolver.toJsonPath(Person.class, "fullName", true));
    }

    @Test
    void passesThroughFieldsWithoutRename() {
        assertEquals("age", resolver.toJsonPath(Person.class, "age", false));
    }

    @Test
    void translatesNestedPathThroughObjectAndCollection() {
        assertEquals("home.zip_code", resolver.toJsonPath(Person.class, "home.zipCode", false));
        // contacts is a List<Contact>; the element's renamed field must resolve
        assertEquals("contacts.email_address", resolver.toJsonPath(Person.class, "contacts.emailAddress", false));
    }

    @Test
    void returnsNullForUnresolvablePath() {
        assertNull(resolver.toJsonPath(Person.class, "doesNotExist", false));
        assertNull(resolver.toJsonPath(Person.class, "home.nope", false));
    }

    @Test
    void honorsPropertyNamingStrategy() {
        ObjectMapper snakeMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
        JsonPropertyPathResolver snakeResolver = new JsonPropertyPathResolver(snakeMapper);

        assertEquals("first_name", snakeResolver.toJsonPath(Person.class, "firstName", true));
    }

    @Test
    void toJsonPathsDropsUnresolvableAndTranslatesRest() {
        Set<String> result = resolver.toJsonPaths(Person.class, Set.of("fullName", "doesNotExist"), false);
        assertEquals(Set.of("full_name"), result);
    }

    @Test
    void toJavaPathTranslatesJsonRenameBackToJavaProperty() {
        assertEquals("fullName", resolver.toJavaPath(Person.class, "full_name"));
    }

    @Test
    void toJavaPathPassesThroughFieldsWithoutRename() {
        assertEquals("age", resolver.toJavaPath(Person.class, "age"));
    }

    @Test
    void toJavaPathLeavesAlreadyJavaNameUnchanged() {
        // A client filtering by the Java attribute name must keep working (no regression).
        assertEquals("fullName", resolver.toJavaPath(Person.class, "fullName"));
    }

    @Test
    void toJavaPathLeavesUnknownSegmentUnchanged() {
        assertEquals("bogus", resolver.toJavaPath(Person.class, "bogus"));
        // First segment resolves, the unknown tail passes through verbatim.
        assertEquals("home.nope", resolver.toJavaPath(Person.class, "home.nope"));
    }

    @Test
    void toJavaPathTranslatesNestedPathThroughObjectAndCollection() {
        assertEquals("home.zipCode", resolver.toJavaPath(Person.class, "home.zip_code"));
        assertEquals("contacts.emailAddress", resolver.toJavaPath(Person.class, "contacts.email_address"));
    }

    @Test
    void toJavaPathHonorsPropertyNamingStrategy() {
        ObjectMapper snakeMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
        JsonPropertyPathResolver snakeResolver = new JsonPropertyPathResolver(snakeMapper);

        assertEquals("firstName", snakeResolver.toJavaPath(Person.class, "first_name"));
    }

    @Getter
    @Setter
    private static class Person {
        @JsonProperty("full_name")
        private String fullName;
        private String firstName;
        private int age;
        private Address home;
        private List<Contact> contacts;
    }

    @Getter
    @Setter
    private static class Address {
        @JsonProperty("zip_code")
        private String zipCode;
    }

    @Getter
    @Setter
    private static class Contact {
        @JsonProperty("email_address")
        private String emailAddress;
    }
}
