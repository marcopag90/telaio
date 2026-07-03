package io.paganbit.telaio.core.registry;

import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.core.exception.DalDefinitionException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.EnumSet;
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
    void convenienceConstructor_shouldDefaultToFullCrudSurface() {
        DalDefinitionEntry definition = new DalDefinitionEntry("testDal", TestEntity.class);

        assertEquals(EnumSet.allOf(DalOperationType.class), definition.exposedOperations());
    }

    @Test
    void constructor_shouldPreserveExposedOperations() {
        DalDefinitionEntry definition = new DalDefinitionEntry(
            "testDal", TestEntity.class, false,
            EnumSet.of(DalOperationType.CREATE, DalOperationType.READ));

        assertEquals(EnumSet.of(DalOperationType.CREATE, DalOperationType.READ), definition.exposedOperations());
    }

    @Test
    void exposedOperations_shouldBeImmutable() {
        DalDefinitionEntry definition = new DalDefinitionEntry("testDal", TestEntity.class);
        var exposedOperations = definition.exposedOperations();

        assertThrows(UnsupportedOperationException.class,
            () -> exposedOperations.add(DalOperationType.CREATE));
    }

    @Test
    void constructor_shouldRejectExposedDalWithNoOperations() {
        var noOperations = EnumSet.noneOf(DalOperationType.class);

        DalDefinitionException exception = assertThrows(
            DalDefinitionException.class,
            () -> new DalDefinitionEntry("testDal", TestEntity.class, false, noOperations));

        assertTrue(exception.getMessage().contains("exposes no operation"));
    }

    @Test
    void constructor_shouldAllowInternalDalWithNoOperations() {
        DalDefinitionEntry definition = new DalDefinitionEntry(
            "testDal", TestEntity.class, true, EnumSet.noneOf(DalOperationType.class));

        assertTrue(definition.exposedOperations().isEmpty());
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
