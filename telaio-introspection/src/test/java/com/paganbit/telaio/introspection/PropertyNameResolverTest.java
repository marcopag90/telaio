package com.paganbit.telaio.introspection;

import org.junit.jupiter.api.Test;

import java.io.Serial;

import static com.paganbit.telaio.introspection.PropertyNameResolver.*;
import static org.junit.jupiter.api.Assertions.*;

class PropertyNameResolverTest {

    record AddressDto(String city) {
    }

    record CustomerDto(Long id, AddressDto address) {
    }

    static class UserDto {

        String getEmail() {
            return "email";
        }

        boolean isActive() {
            return false;
        }
    }

    static class PlainRef implements PropertyRef<String, String> {

        @Override
        public String apply(String s) {
            return s;
        }
    }

    static class FakeLambdaRef implements PropertyRef<String, String> {

        @Override
        public String apply(String s) {
            return s;
        }

        @Serial
        @SuppressWarnings("unused")
        Object writeReplace() {
            return "not a serialized lambda";
        }
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

    @Test
    void propertyNameShouldStripGetPrefixFromBeanAccessor() {
        String name = propertyName(UserDto::getEmail);
        assertEquals("email", name, "Expected 'get' prefix to be stripped and the remainder uncapitalized");
    }

    @Test
    void propertyNameShouldStripIsPrefixFromBooleanAccessor() {
        String name = propertyName(UserDto::isActive);
        assertEquals("active", name, "Expected 'is' prefix to be stripped and the remainder uncapitalized");
    }

    @Test
    void propertyNameShouldFailWhenRefIsNotALambda() {
        PlainRef ref = new PlainRef();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> propertyName(ref),
            "Expected a plain PropertyRef implementation to be rejected");
        assertInstanceOf(NoSuchMethodException.class, ex.getCause(),
            "Expected the missing synthetic writeReplace to be the cause");
        assertThrows(IllegalStateException.class, () -> propertyName(ref),
            "Expected the failure to repeat consistently (nothing cached for a throwing ref)");
    }

    @Test
    void propertyNameShouldFailWhenWriteReplaceIsNotASerializedLambda() {
        FakeLambdaRef ref = new FakeLambdaRef();
        assertThrows(IllegalArgumentException.class, () -> propertyName(ref),
            "Expected a non-SerializedLambda serialized form to be rejected");
    }
}
