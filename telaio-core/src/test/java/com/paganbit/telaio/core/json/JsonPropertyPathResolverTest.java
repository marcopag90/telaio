package com.paganbit.telaio.core.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
    void resolveJavaPathReportsUnknownSegmentOnBeanTypes() {
        JsonPropertyPathResolver.JavaPathResolution root = resolver.resolveJavaPath(Person.class, "bogus");
        assertFalse(root.resolved());
        assertEquals("bogus", root.unresolvedSegment());
        assertEquals("bogus", root.javaPath());

        JsonPropertyPathResolver.JavaPathResolution nested = resolver.resolveJavaPath(Person.class, "home.nope");
        assertFalse(nested.resolved());
        assertEquals("nope", nested.unresolvedSegment());
        // The resolvable prefix is still translated; the unknown tail passes through verbatim.
        assertEquals("home.nope", nested.javaPath());

        JsonPropertyPathResolver.JavaPathResolution element =
            resolver.resolveJavaPath(Person.class, "contacts.phone.number");
        assertEquals("phone", element.unresolvedSegment());
    }

    @Test
    void resolveJavaPathAcceptsJsonAndJavaNames() {
        assertEquals("fullName", resolver.resolveJavaPath(Person.class, "full_name").javaPath());
        JsonPropertyPathResolver.JavaPathResolution javaName = resolver.resolveJavaPath(Person.class, "fullName");
        assertTrue(javaName.resolved());
        assertEquals("fullName", javaName.javaPath());
        assertEquals("home.zipCode", resolver.resolveJavaPath(Person.class, "home.zipCode").javaPath());
    }

    @Test
    void resolveJavaPathStopsCheckingAfterMapObjectOrJsonNode() {
        // Map keys are dynamic: anything after the map passes through unchecked.
        JsonPropertyPathResolver.JavaPathResolution map = resolver.resolveJavaPath(Person.class, "attributes.color");
        assertTrue(map.resolved());
        assertEquals("attributes.color", map.javaPath());
        assertTrue(resolver.resolveJavaPath(Person.class, "attributes.color.shade").resolved());
        // Object and JsonNode carry no introspectable shape either.
        assertTrue(resolver.resolveJavaPath(Person.class, "payload.anything").resolved());
        assertTrue(resolver.resolveJavaPath(Person.class, "raw.deep.key").resolved());
        // A renamed map property is still translated before the opaque part.
        assertEquals("extraAttributes.k", resolver.resolveJavaPath(Person.class, "extra_attributes.k").javaPath());
    }

    @Test
    void resolveJavaPathPassesReferenceKeySegmentsThroughButRejectsOtherDollarSegments() {
        // Reference accessors (the keys of a stored document reference) are never checked against the bean.
        for (String key : List.of("$id", "$ref", "$db")) {
            JsonPropertyPathResolver.JavaPathResolution ref = resolver.resolveJavaPath(Person.class, "home." + key);
            assertTrue(ref.resolved());
            assertEquals("home." + key, ref.javaPath());
        }
        // Any other $-segment is just an unknown field.
        assertEquals("$x", resolver.resolveJavaPath(Person.class, "home.$x").unresolvedSegment());
    }

    @Test
    void resolveJavaPathRejectsSegmentOnScalar() {
        JsonPropertyPathResolver.JavaPathResolution scalar = resolver.resolveJavaPath(Person.class, "age.digits");
        assertFalse(scalar.resolved());
        assertEquals("digits", scalar.unresolvedSegment());
        assertEquals("length", resolver.resolveJavaPath(Person.class, "full_name.length").unresolvedSegment());
        // JDK value types expose getters (UUID#getLeastSignificantBits, Instant#getNano) but are leaves.
        assertEquals("leastSignificantBits",
            resolver.resolveJavaPath(Person.class, "externalId.leastSignificantBits").unresolvedSegment());
        assertEquals("nano", resolver.resolveJavaPath(Person.class, "createdAt.nano").unresolvedSegment());
        assertEquals("declaringClass",
            resolver.resolveJavaPath(Person.class, "kind.declaringClass").unresolvedSegment());
    }

    @Test
    void resolveJavaPathAcceptsJavaNameOfPropertiesJacksonDoesNotExpose() {
        // A @JsonIgnore'd field has no wire name, but server-side filters may still address it by Java name.
        JsonPropertyPathResolver.JavaPathResolution ignored = resolver.resolveJavaPath(Person.class, "secret");
        assertTrue(ignored.resolved());
        assertEquals("secret", ignored.javaPath());
    }

    @Test
    void resolveJavaPathLetsWireNamesWinOverJavaNamesThatSpellTheSame() {
        // "id" is the wire name of externalRef and, at the same time, the Java name of the renamed id field:
        // the wire name must win regardless of Jackson's property iteration order.
        assertEquals("externalRef", resolver.resolveJavaPath(Aliased.class, "id").javaPath());
        assertEquals("id", resolver.resolveJavaPath(Aliased.class, "internal_id").javaPath());
        assertEquals("externalRef", resolver.resolveJavaPath(Aliased.class, "externalRef").javaPath());
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
        private Map<String, String> attributes;
        @JsonProperty("extra_attributes")
        private Map<String, Object> extraAttributes;
        private Object payload;
        private JsonNode raw;
        private UUID externalId;
        private Instant createdAt;
        private Kind kind;
        @JsonIgnore
        private String secret;
    }

    enum Kind {A, B}

    @Getter
    @Setter
    private static class Aliased {
        @JsonProperty("internal_id")
        private Long id;
        @JsonProperty("id")
        private String externalRef;
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
