package com.paganbit.telaio.introspection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeUtilTest {

    @Test
    void testPrimitiveAndWrapperTypes() {
        // Primitive types and their wrappers should not be complex
        assertFalse(TypeUtil.isComplexType(int.class), "int should not be complex");
        assertFalse(TypeUtil.isComplexType(Integer.class), "Integer should not be complex");
        assertFalse(TypeUtil.isComplexType(long.class), "long should not be complex");
        assertFalse(TypeUtil.isComplexType(Long.class), "Long should not be complex");
        assertFalse(TypeUtil.isComplexType(double.class), "double should not be complex");
        assertFalse(TypeUtil.isComplexType(Double.class), "Double should not be complex");
        assertFalse(TypeUtil.isComplexType(float.class), "float should not be complex");
        assertFalse(TypeUtil.isComplexType(Float.class), "Float should not be complex");
        assertFalse(TypeUtil.isComplexType(boolean.class), "boolean should not be complex");
        assertFalse(TypeUtil.isComplexType(Boolean.class), "Boolean should not be complex");
        assertFalse(TypeUtil.isComplexType(char.class), "char should not be complex");
        assertFalse(TypeUtil.isComplexType(Character.class), "Character should not be complex");
        assertFalse(TypeUtil.isComplexType(byte.class), "byte should not be complex");
        assertFalse(TypeUtil.isComplexType(Byte.class), "Byte should not be complex");
        assertFalse(TypeUtil.isComplexType(short.class), "short should not be complex");
        assertFalse(TypeUtil.isComplexType(Short.class), "Short should not be complex");
    }

    @Test
    void testStringAndEnumTypes() {
        // String and Enum types should not be complex
        assertFalse(TypeUtil.isComplexType(String.class), "String should not be complex");
        assertFalse(TypeUtil.isComplexType(TestEnum.class), "Enum should not be complex");
    }

    @Test
    void testDateTypes() {
        // Date types should not be complex
        assertFalse(TypeUtil.isComplexType(Date.class), "Date should not be complex");
        assertFalse(TypeUtil.isComplexType(java.sql.Date.class), "java.sql.Date should not be complex");
        assertFalse(TypeUtil.isComplexType(java.sql.Timestamp.class), "Timestamp should not be complex");
    }

    @Test
    void testTemporalTypes() {
        // Java 8 temporal types should not be complex
        assertFalse(TypeUtil.isComplexType(Instant.class), "Instant should not be complex");
        assertFalse(TypeUtil.isComplexType(LocalDate.class), "LocalDate should not be complex");
        assertFalse(TypeUtil.isComplexType(LocalTime.class), "LocalTime should not be complex");
        assertFalse(TypeUtil.isComplexType(LocalDateTime.class), "LocalDateTime should not be complex");
        assertFalse(TypeUtil.isComplexType(ZonedDateTime.class), "ZonedDateTime should not be complex");
        assertFalse(TypeUtil.isComplexType(OffsetTime.class), "OffsetTime should not be complex");
        assertFalse(TypeUtil.isComplexType(OffsetDateTime.class), "OffsetDateTime should not be complex");
    }

    @Test
    void testCollectionTypes() {
        // Collection types should not be complex
        assertFalse(TypeUtil.isComplexType(List.class), "List should not be complex");
        assertFalse(TypeUtil.isComplexType(ArrayList.class), "ArrayList should not be complex");
        assertFalse(TypeUtil.isComplexType(Set.class), "Set should not be complex");
        assertFalse(TypeUtil.isComplexType(HashSet.class), "HashSet should not be complex");
        assertFalse(TypeUtil.isComplexType(Map.class), "Map should not be complex");
        assertFalse(TypeUtil.isComplexType(HashMap.class), "HashMap should not be complex");
    }

    @Test
    void testArrayTypes() {
        // Array types should not be complex
        assertFalse(TypeUtil.isComplexType(int[].class), "int[] should not be complex");
        assertFalse(TypeUtil.isComplexType(String[].class), "String[] should not be complex");
        assertFalse(TypeUtil.isComplexType(Object[].class), "Object[] should not be complex");
    }

    @Test
    void testOtherCommonTypes() {
        // Other common types should not be complex
        assertFalse(TypeUtil.isComplexType(UUID.class), "UUID should not be complex");
        assertFalse(TypeUtil.isComplexType(BigDecimal.class), "BigDecimal should not be complex");
        assertFalse(TypeUtil.isComplexType(BigInteger.class), "BigInteger should not be complex");
        assertFalse(TypeUtil.isComplexType(Optional.class), "Optional should not be complex");
    }

    @Test
    void testComplexTypes() {
        // Custom classes should be complex
        assertTrue(TypeUtil.isComplexType(TestClass.class), "Custom class should be complex");
    }

    // Test enum
    enum TestEnum {
        VALUE1, VALUE2
    }

    // Test complex class
    static class TestClass {
    }

}
