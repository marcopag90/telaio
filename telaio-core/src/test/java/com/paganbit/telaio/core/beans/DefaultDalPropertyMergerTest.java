package com.paganbit.telaio.core.beans;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultDalPropertyMergerTest {

    private DefaultDalPropertyMerger merger;

    @BeforeEach
    void setUp() {
        merger = new DefaultDalPropertyMerger(JsonMapper.builder().build());
    }

    @Test
    void shouldMergeSimpleAndNestedProperties() {
        TestEntity entity = new TestEntity();
        entity.setName("Before");
        entity.setAge(10);
        entity.setAddress(new Address());

        Map<String, Object> patch = new HashMap<>();
        patch.put("name", "After");
        patch.put("age", "42"); // string coerced to Integer
        patch.put("address", Map.of(
            "city", "Paris",
            "place", Map.of("name", "Eiffel Tower")
        ));

        merger.merge(patch, entity);

        assertEquals("After", entity.getName());
        assertEquals(42, entity.getAge());
        assertNotNull(entity.getAddress());
        assertEquals("Paris", entity.getAddress().getCity());
        assertEquals("Eiffel Tower", entity.getAddress().getPlace().getName());
    }

    @Test
    void shouldDeepMergeNestedObjectPreservingUntouchedFields() {
        // RFC 7396: nested object is merged, not replaced -> untouched nested fields survive.
        TestEntity entity = new TestEntity();
        Address address = new Address();
        address.setCity("Rome");
        address.setZipCode("00100");
        entity.setAddress(address);

        merger.merge(Map.of("address", Map.of("city", "Milan")), entity);

        assertEquals("Milan", entity.getAddress().getCity());
        assertEquals("00100", entity.getAddress().getZipCode(), "untouched nested field must be preserved");
    }

    @Test
    void shouldReplaceCollectionsWholesale() {
        // RFC 7396: arrays are replaced, not merged/appended.
        TestEntity entity = new TestEntity();
        entity.setTags(new ArrayList<>(List.of("a", "b", "c")));

        merger.merge(Map.of("tags", List.of("x")), entity);

        assertEquals(List.of("x"), entity.getTags());
    }

    @Test
    void shouldClearPropertyOnExplicitNull() {
        TestEntity entity = new TestEntity();
        entity.setName("Present");

        Map<String, Object> patch = new HashMap<>();
        patch.put("name", null);

        merger.merge(patch, entity);

        assertNull(entity.getName());
    }

    @Test
    void shouldLeaveAbsentKeysUntouched() {
        TestEntity entity = new TestEntity();
        entity.setName("Keep");
        entity.setAge(7);

        merger.merge(Map.of("age", 8), entity);

        assertEquals("Keep", entity.getName());
        assertEquals(8, entity.getAge());
    }

    @Test
    void shouldIgnoreEmptyPatch() {
        TestEntity entity = new TestEntity();
        entity.setName("Unchanged");

        merger.merge(Map.of(), entity);

        assertEquals("Unchanged", entity.getName());
    }

    @Test
    void shouldIgnoreUnknownProperties() {
        // Jackson 3 ignores unknown properties by default, consistently with the create path
        // (convertValue uses the same mapper). Unknown keys are silently dropped.
        TestEntity entity = new TestEntity();
        entity.setName("Kept");

        merger.merge(Map.of("nonExistent", "value"), entity);

        assertEquals("Kept", entity.getName());
    }

    @Getter
    @Setter
    static class TestEntity {
        private @Nullable String name;
        private Integer age;
        private Address address;
        private List<String> tags;
    }

    @Getter
    @Setter
    static class Address {
        private String city;
        private String zipCode;
        private Place place;
    }

    @Getter
    @Setter
    static class Place {
        private String name;
    }
}
