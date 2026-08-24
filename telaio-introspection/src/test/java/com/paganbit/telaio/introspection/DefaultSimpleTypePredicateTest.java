package com.paganbit.telaio.introspection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSimpleTypePredicateTest {

    private DefaultSimpleTypePredicate predicate;

    @BeforeEach
    void setUp() {
        predicate = new DefaultSimpleTypePredicate();
    }

    @Test
    void testWithNull() {
        assertFalse(predicate.test(null));
    }

    @Test
    void testWithArrayTypes() {
        assertTrue(predicate.test(int[].class));
        assertTrue(predicate.test(String[].class));
        assertTrue(predicate.test(Object[].class));
    }

    @Test
    void testWithPrimitiveTypes() {
        assertTrue(predicate.test(int.class));
        assertTrue(predicate.test(boolean.class));
        assertTrue(predicate.test(char.class));
        assertTrue(predicate.test(double.class));
    }

    @Test
    void testWithEnumTypes() {
        assertTrue(predicate.test(TestEnum.class));
    }

    @Test
    void testWithBaseTypes() {
        assertTrue(predicate.test(Boolean.class));
        assertTrue(predicate.test(Character.class));
        assertTrue(predicate.test(String.class));
        assertTrue(predicate.test(UUID.class));
        assertTrue(predicate.test(Optional.class));
    }

    @Test
    void testWithNumberTypes() {
        assertTrue(predicate.test(Integer.class));
        assertTrue(predicate.test(Long.class));
        assertTrue(predicate.test(Double.class));
        assertTrue(predicate.test(Float.class));
        assertTrue(predicate.test(Byte.class));
        assertTrue(predicate.test(Short.class));
        assertTrue(predicate.test(BigDecimal.class));
        assertTrue(predicate.test(BigInteger.class));
    }

    @Test
    void testWithDateAndTemporalTypes() {
        assertTrue(predicate.test(Date.class));
        assertTrue(predicate.test(java.sql.Date.class));
        assertTrue(predicate.test(java.sql.Timestamp.class));
        assertTrue(predicate.test(Instant.class));
        assertTrue(predicate.test(LocalDate.class));
        assertTrue(predicate.test(LocalTime.class));
        assertTrue(predicate.test(LocalDateTime.class));
        assertTrue(predicate.test(ZonedDateTime.class));
        assertTrue(predicate.test(OffsetTime.class));
        assertTrue(predicate.test(OffsetDateTime.class));
    }

    @Test
    void testWithCollectionTypes() {
        assertTrue(predicate.test(List.class));
        assertTrue(predicate.test(ArrayList.class));
        assertTrue(predicate.test(Set.class));
        assertTrue(predicate.test(HashSet.class));
    }

    @Test
    void testWithMapTypes() {
        assertTrue(predicate.test(Map.class));
        assertTrue(predicate.test(HashMap.class));
        assertTrue(predicate.test(TreeMap.class));
    }

    @Test
    void testWithContributedTypes() {
        var withContribution = new DefaultSimpleTypePredicate(Set.of(ContributedType.class));

        assertTrue(withContribution.test(ContributedType.class));
        // Contributions are per-instance: the default predicate stays agnostic.
        assertFalse(predicate.test(ContributedType.class));
    }

    @Test
    void testWithComplexTypes() {
        assertFalse(predicate.test(Object.class));
        assertFalse(predicate.test(DefaultSimpleTypePredicateTest.class));
        assertFalse(predicate.test(ComplexType.class));
    }

    // Helper classes and enums for testing
    private enum TestEnum {
        VALUE1, VALUE2
    }

    private static class ComplexType {
    }

    private static class ContributedType {
    }
}
