package io.paganbit.telaio.core;

import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.builder.StepWithResult;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.beans.DalPropertyMerger;
import io.paganbit.telaio.core.exception.DalEntityNotFoundException;
import io.paganbit.telaio.core.exception.DalEntityValidationException;
import io.paganbit.telaio.core.transaction.DalTransactionPolicy;
import io.paganbit.telaio.core.transaction.FakeTransactionTemplate;
import jakarta.validation.Validator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AbstractDalTest {

    @Mock
    private ConversionService mockConversionService;

    @Mock
    private DalPropertyMerger propertyMerger;

    @Mock
    private Validator mockValidator;

    @Mock
    private SpringValidatorAdapter mockValidatorAdapter;

    @Mock
    private FilterBuilder mockFilterBuilder;

    @Mock
    private FilterNode mockFilterNode;

    @Mock
    private StepWithResult mockStepWithResult;

    @Mock
    private FilterStringConverter mockFilterStringConverter;

    @Mock
    private PlatformTransactionManager mockTransactionManager;

    @Mock
    private DalTransactionPolicy mockTransactionPolicy;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final TransactionTemplate spiedTransactionTemplate = FakeTransactionTemplate.spied();
    private SpringValidatorAdapter validatorAdapter;
    private TestEntityService service;
    private TestEntityService spiedService;

    @BeforeEach
    void setUp() {
        Mockito.reset(
            mockConversionService,
            propertyMerger,
            mockValidator,
            mockValidatorAdapter,
            mockFilterBuilder,
            mockFilterNode,
            mockStepWithResult,
            mockFilterStringConverter,
            mockTransactionManager,
            mockTransactionPolicy,
            spiedTransactionTemplate
        );
        validatorAdapter = new SpringValidatorAdapter(mockValidator);

        service = new TestEntityService();
        service.setObjectMapper(objectMapper);
        service.setPropertyMerger(propertyMerger);
        service.setValidatorAdapter(validatorAdapter);
        service.setFilterBuilder(mockFilterBuilder);
        service.setFilterStringConverter(mockFilterStringConverter);
        service.setTransactionManager(mockTransactionManager);
        service.setTransactionPolicy(mockTransactionPolicy);
        service.afterPropertiesSet();

        spiedService = spy(service);
    }

    @Test
    void testDalInfoResolution() {
        assertEquals(TestEntity.class, service.getEntityClass());
        assertEquals(Long.class, service.getIdClass());
    }

    @Test
    void testTransactionTemplateResolution() {
        final var transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setReadOnly(true);

        final var template = service.finalizeTransaction(mockTransactionManager, transactionDefinition);

        assertNotNull(template);
        assertEquals(transactionDefinition.isReadOnly(), template.isReadOnly());
    }

    @Test
    void testGetters() {
        assertEquals(objectMapper, service.getObjectMapper());
        assertEquals(propertyMerger, service.getPropertyMerger());
        assertEquals(validatorAdapter, service.getValidatorAdapter());
        assertEquals(mockFilterBuilder, service.getFilterBuilder());
        assertEquals(mockFilterStringConverter, service.getFilterStringConverter());
        assertEquals(mockTransactionManager, service.getTransactionManager());
        assertEquals(mockTransactionPolicy, service.getTransactionPolicy());
    }

    @Nested
    class FilterLogicTests {

        @Test
        void testDefaultFilterReturnsNullByDefault() {
            assertNull(service.defaultFilter());
        }

        @Test
        void testCombineFilterWithNullInputReturnsNull() {
            assertNull(service.combineWithDefaultFilter(null));
        }

        @Test
        void testCombineFilterWithSingleCombinedFilter() {
            when(mockFilterBuilder.from(any())).thenReturn(mockStepWithResult);
            when(mockStepWithResult.get()).thenReturn(mockFilterNode);
            when(mockFilterBuilder.and(anyList())).thenReturn(mockStepWithResult);

            service.combineWithDefaultFilter(mockFilterNode);

            verify(mockFilterBuilder, times(1)).from(mockFilterNode);
            verify(mockStepWithResult, times(1)).get();
        }

        @Test
        void testCombineFilterWithDefaultCombinedFilter() {
            service.simulateDefaultFilter = true;
            when(mockFilterBuilder.from(any())).thenReturn(mockStepWithResult);
            when(mockStepWithResult.get()).thenReturn(mockFilterNode);
            when(mockFilterBuilder.and(anyList())).thenReturn(mockStepWithResult);

            final var result = service.combineWithDefaultFilter(mockFilterNode);

            assertNotNull(result);
            verify(mockFilterBuilder, times(1)).from(any());
            verify(mockFilterBuilder, times(1)).and(anyList());
            verify(mockStepWithResult, times(1)).get();
        }
    }

    @Nested
    class CreateTests {

        @Test
        void testCreateSuccess() {
            Map<String, Object> input = Map.of("id", 1L, "name", "Hello");

            when(mockTransactionPolicy.forCreate()).thenReturn(new DefaultTransactionDefinition());
            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );

            TestEntity result = spiedService.create(input);

            assertEquals(1L, result.id);
            assertEquals("Hello", result.name);
            verify(spiedTransactionTemplate, times(1)).execute(any());
            verify(spiedService, times(1)).finalizeBeforeCreate(any());
            verify(spiedService, times(1)).finalizeAfterCreate(any());
        }

        @Test
        @SuppressWarnings("squid:S5778")
        void testCreateValidationError() {
            service.setValidatorAdapter(mockValidatorAdapter);

            doAnswer(invocation -> {
                throw new DalEntityValidationException(List.of());
            }).when(mockValidatorAdapter).validate(any(TestEntity.class), any(BeanPropertyBindingResult.class));

            assertThrows(DalEntityValidationException.class, () -> service.create(Map.of()));
        }
    }

    @Nested
    class ReadTests {

        @Test
        void testRead_withFilterAndPagination_shouldSucceed() {
            final var pageable = PageRequest.of(0, 10, Sort.by("name"));
            final var pageResult = new PageImpl<>(List.of(new TestEntity(), pageable, 1));

            doReturn(mockFilterNode).when(spiedService).combineWithDefaultFilter(mockFilterNode);
            doReturn(pageResult).when(spiedService).executeRead(mockFilterNode, pageable);
            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );
            when(mockTransactionPolicy.forRead()).thenReturn(new DefaultTransactionDefinition());

            Page<TestEntity> page = spiedService.read(mockFilterNode, pageable);

            assertEquals(pageResult, page);
            verify(spiedTransactionTemplate, times(1)).execute(any());
            verify(spiedService, times(1)).combineWithDefaultFilter(mockFilterNode);
            verify(spiedService, times(1)).executeRead(any(FilterNode.class), eq(pageable));
            verify(spiedService, times(1)).finalizeAfterRead(any(TestEntity.class));
        }

        @Test
        void testRead_withFilterAndPagination_withEmptyResult_shouldNotCallFinalizeAfterRead() {
            final var pageable = PageRequest.of(0, 10, Sort.by("name"));
            final var pageResult = new PageImpl<TestEntity>(List.of(), pageable, 0);

            doReturn(mockFilterNode).when(spiedService).combineWithDefaultFilter(mockFilterNode);
            doReturn(pageResult).when(spiedService).executeRead(mockFilterNode, pageable);
            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );
            when(mockTransactionPolicy.forRead()).thenReturn(new DefaultTransactionDefinition());

            Page<TestEntity> page = spiedService.read(mockFilterNode, pageable);

            assertEquals(pageResult, page);
            verify(spiedTransactionTemplate, times(1)).execute(any());
            verify(spiedService, times(1)).combineWithDefaultFilter(mockFilterNode);
            verify(spiedService, times(1)).executeRead(any(FilterNode.class), eq(pageable));
            verify(spiedService, never()).finalizeAfterRead(any(TestEntity.class));
        }

        @Test
        void read_withUnsortedPageable_appliesDefaultSort() {
            final var unsortedPageable = PageRequest.of(0, 10);
            final var defaultSort = Sort.by(Sort.Direction.ASC, "id");
            final var sortedPageable = PageRequest.of(0, 10, defaultSort);
            final var pageResult = new PageImpl<TestEntity>(List.of(), sortedPageable, 0);

            doReturn(mockFilterNode).when(spiedService).combineWithDefaultFilter(mockFilterNode);
            doReturn(pageResult).when(spiedService).executeRead(mockFilterNode, sortedPageable);
            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );
            when(mockTransactionPolicy.forRead()).thenReturn(new DefaultTransactionDefinition());

            spiedService.read(mockFilterNode, unsortedPageable);

            verify(spiedService, times(1)).executeRead(any(FilterNode.class), eq(sortedPageable));
        }

        @Test
        void read_withSortedPageable_doesNotOverrideSort() {
            final var sortedPageable = PageRequest.of(0, 10, Sort.by("name"));
            final var pageResult = new PageImpl<TestEntity>(List.of(), sortedPageable, 0);

            doReturn(mockFilterNode).when(spiedService).combineWithDefaultFilter(mockFilterNode);
            doReturn(pageResult).when(spiedService).executeRead(mockFilterNode, sortedPageable);
            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );
            when(mockTransactionPolicy.forRead()).thenReturn(new DefaultTransactionDefinition());

            spiedService.read(mockFilterNode, sortedPageable);

            verify(spiedService, times(1)).executeRead(any(FilterNode.class), eq(sortedPageable));
        }
    }

    @Nested
    class ReadOneTests {

        @Test
        void testReadOneSuccess_shouldNotCallFinalizeAfterReadOne_onEmptyResult() {
            Long id = 1L;

            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );
            when(mockTransactionPolicy.forReadOne()).thenReturn(new DefaultTransactionDefinition());

            Optional<TestEntity> result = spiedService.readOne(id);

            assertEquals(Optional.empty(), result, "Expected readOne to return empty Optional");
            verify(spiedTransactionTemplate, times(1)).execute(any());
            verify(spiedService, times(1)).executeReadOne(id);
            verify(spiedService, never()).finalizeAfterReadOne(any());
        }

        @Test
        void testReadOneSuccess_shouldCallFinalizeAfterReadOne_onNotEmptyResult() {
            Long id = 1L;

            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );
            when(mockTransactionPolicy.forReadOne()).thenReturn(new DefaultTransactionDefinition());

            spiedService.simulateEmptyResult = false;
            Optional<TestEntity> result = spiedService.readOne(id);

            assertTrue(result.isPresent(), "Expected readOne to return a non-empty Optional");
            verify(spiedTransactionTemplate, times(1)).execute(any());
            verify(spiedService, times(1)).executeReadOne(id);
            verify(spiedService, times(1)).finalizeAfterReadOne(any(TestEntity.class));

            spiedService.simulateEmptyResult = true;
        }
    }

    @Nested
    class UpdateTests {

        @Test
        @SuppressWarnings("squid:S5778")
        void testUpdate_shouldThrowExceptionOnEmptyResult() {
            when(spiedService.executeReadOne(1L)).thenReturn(Optional.empty());

            assertThrows(DalEntityNotFoundException.class, () -> spiedService.update(1L, Map.of()));

            verify(propertyMerger, never()).merge(any(), any());
            verify(spiedService, never()).validate(any());
            verify(spiedTransactionTemplate, never()).execute(any());
        }

        @Test
        void testUpdate_shouldSucceedOnValidInput() {
            Long id = 1L;
            TestEntity existingEntity = new TestEntity();
            existingEntity.id = id;
            Map<String, Object> properties = Map.of();

            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );
            when(mockTransactionPolicy.forReadOne()).thenReturn(new DefaultTransactionDefinition());
            when(mockTransactionPolicy.forUpdate()).thenReturn(new DefaultTransactionDefinition());
            doNothing().when(propertyMerger).merge(properties, existingEntity);
            doNothing().when(spiedService).validate(existingEntity);
            when(spiedService.executeReadOne(id)).thenReturn(Optional.of(existingEntity));
            when(spiedService.executeReadOne(id)).thenReturn(Optional.of(existingEntity));

            TestEntity updatedEntity = spiedService.update(id, properties).orElse(null);

            assertNotNull(updatedEntity);
            assertEquals(id, updatedEntity.id);

            verify(spiedTransactionTemplate, times(2)).execute(any());
            verify(spiedService, times(1)).finalizeBeforeUpdate(any(TestEntity.class));
            verify(spiedService, times(1)).finalizeAfterUpdate(any(TestEntity.class));
            verify(spiedService, times(1)).finalizeAfterReadOne(any(TestEntity.class));
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void testDelete() {
            Long id = 1L;

            doReturn(spiedTransactionTemplate).when(spiedService).finalizeTransaction(
                any(PlatformTransactionManager.class),
                any(DefaultTransactionDefinition.class)
            );
            when(mockTransactionPolicy.forDelete()).thenReturn(new DefaultTransactionDefinition());

            spiedService.simulateEmptyResult = false;
            spiedService.delete(id);
            spiedService.simulateEmptyResult = true;

            verify(spiedTransactionTemplate, times(1)).execute(any());
            verify(spiedService, times(1)).executeReadOne(id);
            verify(spiedService, times(1)).finalizeBeforeDelete(id);
            verify(spiedService, times(1)).executeDelete(id);
            verify(spiedService, times(1)).finalizeAfterDelete(any());
        }

        @Test
        void testDelete_shouldThrowNotFoundWhenEntityIsMissingOrHidden() {
            // executeReadOne is the single defaultFilter enforcement point for by-id operations:
            // an entity hidden by the filter resolves to empty, exactly like a missing one
            // (simulateEmptyResult is true by default).
            assertThrows(DalEntityNotFoundException.class, () -> spiedService.delete(1L));

            verify(spiedService, never()).finalizeBeforeDelete(any());
            verify(spiedService, never()).executeDelete(any());
            verify(spiedService, never()).finalizeAfterDelete(any());
            verify(mockTransactionPolicy, never()).forDelete();
        }
    }


    static class TestEntity {
        @Nullable
        public Long id;
        @Nullable
        public String name;
    }

    static class TestEntityService extends AbstractDal<TestEntity, Long> {

        boolean simulateEmptyResult = true;
        boolean simulateDefaultFilter = false;

        @Nullable
        private FilterNode filterNode;

        TestEntityService() {

        }

        @Override
        @Nullable
        protected FilterNode defaultFilter() {
            return simulateDefaultFilter ? filterNode : null;
        }

        @Override
        protected Sort defaultSort() {
            return Sort.by(Sort.Direction.ASC, "id");
        }

        @Override
        protected TestEntity executeCreate(TestEntity entity) {
            return entity;
        }

        @Override
        protected Page<TestEntity> executeRead(@Nullable FilterNode filter, Pageable pageable) {
            return mock();
        }

        @Override
        protected Optional<TestEntity> executeReadOne(Long id) {
            if (simulateEmptyResult) {
                return Optional.empty();
            }
            return Optional.of(new TestEntity());
        }

        @Override
        protected TestEntity executeUpdate(TestEntity entity) {
            return entity;
        }

        @Override
        protected void executeDelete(Long id) {
            //noop
        }
    }
}
