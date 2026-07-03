package io.paganbit.telaio.core.beans.registration;

import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.core.exception.DalDefinitionException;
import io.paganbit.telaio.core.registry.DalDefinitionEntry;
import io.paganbit.telaio.core.registry.DalManager;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalDefinitionBeanPostProcessorTest {

    @Mock
    private DalManager dalManager;

    private DalDefinitionBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DalDefinitionBeanPostProcessor(dalManager);
    }

    @Test
    void postProcessAfterInitialization_withValidDalService_shouldRegisterIdentity() {
        TestDalService testDalService = new TestDalService();

        Object result = processor.postProcessAfterInitialization(testDalService, "testDalService");

        assertSame(testDalService, result);
        ArgumentCaptor<DalDefinitionEntry> captor = ArgumentCaptor.forClass(DalDefinitionEntry.class);
        verify(dalManager).register(captor.capture());
        assertEquals("testDal", captor.getValue().name());
        assertEquals(TestDalService.class, captor.getValue().dalClass());
        assertFalse(captor.getValue().internal());
    }

    @Test
    void postProcessAfterInitialization_withDefaultDalService_shouldExposeFullCrudSurface() {
        TestDalService testDalService = new TestDalService();

        processor.postProcessAfterInitialization(testDalService, "testDalService");

        ArgumentCaptor<DalDefinitionEntry> captor = ArgumentCaptor.forClass(DalDefinitionEntry.class);
        verify(dalManager).register(captor.capture());
        assertEquals(EnumSet.allOf(DalOperationType.class), captor.getValue().exposedOperations());
    }

    @Test
    void postProcessAfterInitialization_withPartialDalService_shouldExposeOnlyDeclaredOperations() {
        PartialDalService partialDalService = new PartialDalService();

        processor.postProcessAfterInitialization(partialDalService, "partialDalService");

        ArgumentCaptor<DalDefinitionEntry> captor = ArgumentCaptor.forClass(DalDefinitionEntry.class);
        verify(dalManager).register(captor.capture());
        assertEquals(
            EnumSet.of(DalOperationType.CREATE, DalOperationType.READ),
            captor.getValue().exposedOperations());
    }

    @Test
    void postProcessAfterInitialization_withInternalDalService_shouldRegisterAsInternal() {
        InternalDalService internalDalService = new InternalDalService();

        processor.postProcessAfterInitialization(internalDalService, "internalDalService");

        ArgumentCaptor<DalDefinitionEntry> captor = ArgumentCaptor.forClass(DalDefinitionEntry.class);
        verify(dalManager).register(captor.capture());
        assertEquals("internalDal", captor.getValue().name());
        assertTrue(captor.getValue().internal());
    }

    @Test
    void postProcessAfterInitialization_withNonDalService_shouldReturnSameBean() {
        Object nonDalBean = new Object();

        Object result = processor.postProcessAfterInitialization(nonDalBean, "nonDalBean");

        assertSame(nonDalBean, result);
        verifyNoInteractions(dalManager);
    }

    @Test
    void postProcessAfterInitialization_withDalServiceMissingAnnotation_shouldReturnSameBean() {
        DalServiceWithoutAnnotation dalService = new DalServiceWithoutAnnotation();

        Object result = processor.postProcessAfterInitialization(dalService, "dalServiceWithoutAnnotation");

        assertSame(dalService, result);
        verifyNoInteractions(dalManager);
    }

    @Test
    void postProcessAfterInitialization_withNonDalServiceWithAnnotation_shouldThrowException() {
        NonDalServiceWithAnnotation bean = new NonDalServiceWithAnnotation();

        DalDefinitionException exception = assertThrows(
            DalDefinitionException.class,
            () -> processor.postProcessAfterInitialization(bean, "nonDalServiceWithAnnotation")
        );

        assertTrue(exception.getMessage().contains("does not implement"));
        verifyNoInteractions(dalManager);
    }

    @Test
    void postProcessAfterInitialization_whenRegisterFails_shouldWrapInDalDefinitionException() {
        TestDalService testDalService = new TestDalService();
        doThrow(new RuntimeException("Test exception")).when(dalManager).register(any());

        DalDefinitionException exception = assertThrows(
            DalDefinitionException.class,
            () -> processor.postProcessAfterInitialization(testDalService, "testDalService")
        );

        assertTrue(exception.getMessage().contains("Failed to register DAL bean"));
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertEquals("Test exception", exception.getCause().getMessage());
    }

    @DalService(name = "testDal")
    static class TestDalService implements Dal<TestEntity, Long> {

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
            return Optional.empty();
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

    @DalService(name = "internalDal", internal = true)
    static class InternalDalService extends TestDalService {
    }

    @DalService(name = "partialDal", operations = {DalOperationType.CREATE, DalOperationType.READ})
    static class PartialDalService extends TestDalService {
    }

    static class TestEntity {
    }

    static class DalServiceWithoutAnnotation implements Dal<TestEntity, Long> {

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
            return Optional.empty();
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

    @DalService(name = "nonDalService")
    static class NonDalServiceWithAnnotation {
    }
}
