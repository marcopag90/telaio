package com.paganbit.telaio.core.registry;

import com.paganbit.telaio.core.Dal;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DalDefinitionEntryTest {

    @Test
    void constructor_shouldInitializeAllFields() {
        DalDefinitionEntry definition = new DalDefinitionEntry("testDal", TestEntity.class, true);

        assertEquals("testDal", definition.name());
        assertEquals(TestEntity.class, definition.dalClass());
        assertTrue(definition.internal());
    }

    @Test
    void convenienceConstructor_shouldDefaultToExposed() {
        DalDefinitionEntry definition = new DalDefinitionEntry("testDal", TestEntity.class);

        assertFalse(definition.internal());
    }

    @Test
    void equals_shouldReturnTrue_whenObjectsAreEqual() {
        DalDefinitionEntry definition1 = new DalDefinitionEntry("testDal", TestEntity.class);
        DalDefinitionEntry definition2 = new DalDefinitionEntry("testDal", TestEntity.class);

        assertEquals(definition1, definition2);
        assertEquals(definition1.hashCode(), definition2.hashCode());
    }

    @Test
    void equals_shouldReturnFalse_whenObjectsAreDifferent() {
        DalDefinitionEntry definition1 = new DalDefinitionEntry("testDal1", TestEntity.class);
        DalDefinitionEntry definition2 = new DalDefinitionEntry("testDal2", TestEntity.class);

        assertNotEquals(definition1, definition2);
    }

    @Test
    void toString_shouldContainAllFieldValues() {
        DalDefinitionEntry definition = new DalDefinitionEntry("testDal", TestEntity.class);

        String toString = definition.toString();

        assertTrue(toString.contains("testDal"));
        assertTrue(toString.contains(TestEntity.class.getName()));
    }

    private static class TestEntity implements Dal<TestEntity, Long> {

        @Override
        public TestEntity create(Map<String, Object> properties) {
            return mock();
        }

        @Override
        public Page<TestEntity> read(@Nullable FilterNode filter, Pageable pageable) {
            return mock();
        }

        @Override
        public Optional<TestEntity> readOne(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<TestEntity> update(Long id, Map<String, Object> properties) {
            return mock();
        }

        @Override
        public void delete(Long id) {
            //noop
        }

        @Override
        public Class<TestEntity> getEntityClass() {
            return TestEntity.class;
        }

        @Override
        public Class<Long> getIdClass() {
            return Long.class;
        }
    }
}
