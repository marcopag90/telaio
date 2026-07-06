package com.paganbit.telaio.introspection;

import org.junit.jupiter.api.Test;

import static com.paganbit.telaio.introspection.PropertyNameResolver.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PropertyNameResolverTest {

    record AddressDto(String city) {
    }

    record CustomerDto(Long id, AddressDto address) {
    }

    @Test
    void propertyNameShouldReturnSimplePropertyName() {
        String name = propertyName(CustomerDto::id);
        assertEquals("id", name, "Expected property name to be 'id'");
    }

    @Test
    void propertyPathShouldConcatenatePropertiesCorrectly() {
        String pathStr = propertyPath("address", "city");
        assertEquals("address.city", pathStr, "Expected path to be 'address.city'");
    }

    @Test
    void propertyNameShouldBeIdempotentAcrossRepeatedCalls() {
        String first = propertyName(CustomerDto::id);
        String second = propertyName(CustomerDto::id);
        assertEquals("id", first, "Expected property name to be 'id'");
        assertEquals(first, second, "Expected memoized result to be stable across calls");
    }

    @Test
    void builderShouldResolveChainedPropertyPath() {
        String nested = PropertyPathBuilder.of(CustomerDto::address)
            .then(AddressDto::city)
            .build();
        assertEquals("address.city", nested, "Expected full path to be 'address.city'");
    }
}
