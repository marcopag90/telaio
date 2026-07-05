package io.paganbit.telaio.jpa;

import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterSpecification;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.beans.DalPropertyMerger;
import io.paganbit.telaio.core.exception.DalEntityNotFoundException;
import io.paganbit.telaio.core.transaction.DalTransactionPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link JpaDal}'s setter-based wiring and {@link JpaDal#afterPropertiesSet()}
 * lifecycle. The Spring-managed end-to-end path (component scanning, generic-aware repository
 * autowiring, CGLIB interception) is exercised by the showcase {@code @SpringBootTest}, which boots
 * five empty-body {@code JpaDal} subclasses over distinct entity repositories.
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JpaDalTest {

    static class TestEntity {
    }

    /**
     * Empty-body subclass mirroring the way concrete DALs are declared in a Spring context.
     */
    static class TestJpaDal extends JpaDal<TestEntity, Long> {

        TestJpaDal() {
            super();
        }

        TestJpaDal(JpaDalRepository<TestEntity, Long> repository, EntityManager entityManager) {
            super(repository, entityManager);
        }
    }

    /**
     * Subclass exposing a non-null {@code defaultFilter()} to exercise specification composition.
     */
    static class DefaultFilteredJpaDal extends TestJpaDal {

        private final FilterNode defaultFilter;

        DefaultFilteredJpaDal(
            JpaDalRepository<TestEntity, Long> repository,
            EntityManager entityManager,
            FilterNode defaultFilter
        ) {
            super(repository, entityManager);
            this.defaultFilter = defaultFilter;
        }

        @Override
        protected FilterNode defaultFilter() {
            return defaultFilter;
        }
    }

    @Mock
    private JpaDalRepository<TestEntity, Long> repository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Metamodel metamodel;

    @Mock
    private EntityType<TestEntity> entityType;

    @Mock
    private Validator validator;

    @Mock
    private DalPropertyMerger propertyMerger;

    @Mock
    private FilterBuilder filterBuilder;

    @Mock
    private FilterStringConverter filterStringConverter;

    @Mock
    private FilterSpecificationConverter specificationConverter;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private DalTransactionPolicy transactionPolicy;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUpMetamodel() {
        lenient().when(entityManager.getMetamodel()).thenReturn(metamodel);
        lenient().when(metamodel.entity(TestEntity.class)).thenReturn(entityType);
    }

    /**
     * Injects the seven {@link io.paganbit.telaio.core.AbstractDal} collaborators required by init.
     */
    private void wireAbstractDalCollaborators(TestJpaDal dal) {
        dal.setObjectMapper(objectMapper);
        dal.setValidatorAdapter(new SpringValidatorAdapter(validator));
        dal.setPropertyMerger(propertyMerger);
        dal.setFilterBuilder(filterBuilder);
        dal.setFilterStringConverter(filterStringConverter);
        dal.setTransactionManager(transactionManager);
        dal.setTransactionPolicy(transactionPolicy);
    }

    /**
     * Returns a fully-initialized DAL (constructor + collaborators + specification converter + init).
     */
    private TestJpaDal readyDal() {
        TestJpaDal dal = new TestJpaDal(repository, entityManager);
        wireAbstractDalCollaborators(dal);
        dal.setSpecificationConverter(specificationConverter);
        dal.afterPropertiesSet();
        return dal;
    }

    @Test
    void afterPropertiesSet_viaConstructor_computesEntityTypeAndExposesGetters() {
        TestJpaDal dal = new TestJpaDal(repository, entityManager);
        wireAbstractDalCollaborators(dal);
        dal.setSpecificationConverter(specificationConverter);

        dal.afterPropertiesSet();

        assertThat(dal.getEntityClass()).isEqualTo(TestEntity.class);
        assertThat(dal.getIdClass()).isEqualTo(Long.class);
        assertThat(dal.getRepository()).isSameAs(repository);
        assertThat(dal.getEntityType()).isSameAs(entityType);
    }

    @Test
    void afterPropertiesSet_viaSetters_computesEntityTypeAndExposesGetters() {
        TestJpaDal dal = new TestJpaDal();
        dal.setRepository(repository);
        dal.setEntityManager(entityManager);
        wireAbstractDalCollaborators(dal);

        dal.afterPropertiesSet();

        assertThat(dal.getRepository()).isSameAs(repository);
        assertThat(dal.getEntityType()).isSameAs(entityType);
    }

    @Test
    void afterPropertiesSet_missingRepository_throwsWithClearMessage() {
        TestJpaDal dal = new TestJpaDal();
        dal.setEntityManager(entityManager);
        wireAbstractDalCollaborators(dal);

        assertThatNullPointerException()
            .isThrownBy(dal::afterPropertiesSet)
            .withMessageContaining("JpaDalRepository")
            .withMessageContaining(TestEntity.class.getSimpleName());
    }

    @Test
    void afterPropertiesSet_missingEntityManager_throwsWithClearMessage() {
        TestJpaDal dal = new TestJpaDal();
        dal.setRepository(repository);
        wireAbstractDalCollaborators(dal);

        assertThatNullPointerException()
            .isThrownBy(dal::afterPropertiesSet)
            .withMessageContaining("EntityManager");
    }

    @Test
    void setRepository_null_throws() {
        TestJpaDal dal = new TestJpaDal();
        assertThatNullPointerException().isThrownBy(() -> dal.setRepository(null));
    }

    @Test
    void setEntityManager_null_throws() {
        TestJpaDal dal = new TestJpaDal();
        assertThatNullPointerException().isThrownBy(() -> dal.setEntityManager(null));
    }

    @Test
    void setSpecificationConverter_null_throws() {
        TestJpaDal dal = new TestJpaDal();
        assertThatNullPointerException().isThrownBy(() -> dal.setSpecificationConverter(null));
    }

    // -----------------------------------------------------------------------------------------------
    // CRUD delegation to the repository
    // -----------------------------------------------------------------------------------------------

    @Test
    void executeCreate_delegatesToRepositorySave() {
        TestJpaDal dal = readyDal();
        TestEntity entity = new TestEntity();
        TestEntity saved = new TestEntity();
        doReturn(saved).when(repository).save(entity);

        assertThat(dal.executeCreate(entity)).isSameAs(saved);
        verify(repository).save(entity);
    }

    @Test
    void executeUpdate_delegatesToRepositorySave() {
        TestJpaDal dal = readyDal();
        TestEntity entity = new TestEntity();
        TestEntity saved = new TestEntity();
        doReturn(saved).when(repository).save(entity);

        assertThat(dal.executeUpdate(entity)).isSameAs(saved);
        verify(repository).save(entity);
    }

    @Test
    void executeDelete_delegatesToRepositoryDeleteById() {
        TestJpaDal dal = readyDal();

        dal.executeDelete(42L);

        verify(repository).deleteById(42L);
    }

    @Test
    void executeRead_withoutFilter_usesPageableOverloadAndSkipsConverter() {
        TestJpaDal dal = readyDal();
        Pageable pageable = PageRequest.of(0, 10);
        Page<TestEntity> page = new PageImpl<>(List.of(new TestEntity()));
        doReturn(page).when(repository).findAll(pageable);

        assertThat(dal.executeRead(null, pageable)).isSameAs(page);
        verify(repository).findAll(pageable);
        verifyNoInteractions(specificationConverter);
    }

    @Test
    void executeRead_withFilter_convertsAndUsesSpecificationOverload() {
        TestJpaDal dal = readyDal();
        Pageable pageable = PageRequest.of(0, 10);
        FilterNode filter = mock(FilterNode.class);
        @SuppressWarnings("unchecked")
        FilterSpecification<TestEntity> spec = mock(FilterSpecification.class);
        Page<TestEntity> page = new PageImpl<>(List.of(new TestEntity()));
        doReturn(spec).when(specificationConverter).convert(filter);
        doReturn(page).when(repository).findAll(spec, pageable);

        assertThat(dal.executeRead(filter, pageable)).isSameAs(page);
        verify(specificationConverter).convert(filter);
        verify(repository).findAll(spec, pageable);
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeReadOne_delegatesToFindOne() {
        TestJpaDal dal = readyDal();
        TestEntity entity = new TestEntity();
        doReturn(Optional.of(entity)).when(repository).findOne(any(Specification.class));

        assertThat(dal.executeReadOne(1L)).contains(entity);
        verify(repository).findOne(any(Specification.class));
    }

    // -----------------------------------------------------------------------------------------------
    // Default-filter specification composition
    // -----------------------------------------------------------------------------------------------

    @Test
    void combineWithDefaultSpecification_noDefaultFilter_returnsSameSpecification() {
        TestJpaDal dal = readyDal();
        Specification<TestEntity> input = (root, query, cb) -> null;

        assertThat(dal.combineWithDefaultSpecification(input)).isSameAs(input);
        verifyNoInteractions(specificationConverter);
    }

    @Test
    void combineWithDefaultSpecification_withDefaultFilter_composesViaConverter() {
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredJpaDal dal = new DefaultFilteredJpaDal(repository, entityManager, defaultFilter);
        wireAbstractDalCollaborators(dal);
        dal.setSpecificationConverter(specificationConverter);
        dal.afterPropertiesSet();
        Specification<TestEntity> input = (root, query, cb) -> null;
        @SuppressWarnings("unchecked")
        FilterSpecification<TestEntity> defaultSpec = mock(FilterSpecification.class);
        doReturn(defaultSpec).when(specificationConverter).convert(defaultFilter);

        Specification<TestEntity> result = dal.combineWithDefaultSpecification(input);

        assertThat(result).isNotNull().isNotSameAs(input);
        verify(specificationConverter).convert(defaultFilter);
    }

    // -----------------------------------------------------------------------------------------------
    // Delete honors the default filter (by-id operations go through the filtered lookup)
    // -----------------------------------------------------------------------------------------------

    /**
     * Returns a fully initialized DAL whose {@code defaultFilter()} converts to the given
     * specification, so the filtered by-id lookup is exercised end-to-end at the mock level.
     */
    private DefaultFilteredJpaDal filteredDal(FilterNode defaultFilter) {
        DefaultFilteredJpaDal dal = new DefaultFilteredJpaDal(repository, entityManager, defaultFilter);
        wireAbstractDalCollaborators(dal);
        dal.setSpecificationConverter(specificationConverter);
        dal.afterPropertiesSet();
        return dal;
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_entityHiddenByDefaultFilter_throwsNotFoundAndNeverDeletes() {
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredJpaDal dal = filteredDal(defaultFilter);
        FilterSpecification<TestEntity> defaultSpec = mock(FilterSpecification.class);
        doReturn(defaultSpec).when(specificationConverter).convert(defaultFilter);
        // The filtered by-id lookup does not see the row: hidden behaves exactly like missing.
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertThatExceptionOfType(DalEntityNotFoundException.class)
            .isThrownBy(() -> dal.delete(42L));

        verify(repository, never()).deleteById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void update_entityHiddenByDefaultFilter_throwsNotFoundAndNeverSaves() {
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredJpaDal dal = filteredDal(defaultFilter);
        FilterSpecification<TestEntity> defaultSpec = mock(FilterSpecification.class);
        doReturn(defaultSpec).when(specificationConverter).convert(defaultFilter);
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertThatExceptionOfType(DalEntityNotFoundException.class)
            .isThrownBy(() -> dal.update(42L, Map.of()));

        verify(repository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_visibleEntity_deletesById() {
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredJpaDal dal = filteredDal(defaultFilter);
        FilterSpecification<TestEntity> defaultSpec = mock(FilterSpecification.class);
        doReturn(defaultSpec).when(specificationConverter).convert(defaultFilter);
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(new TestEntity()));
        when(transactionPolicy.forDelete()).thenReturn(new DefaultTransactionDefinition());

        dal.delete(42L);

        verify(repository).findOne(any(Specification.class));
        verify(repository).deleteById(42L);
    }

    // -----------------------------------------------------------------------------------------------
    // Guarded accessors before initialization
    // -----------------------------------------------------------------------------------------------

    @Test
    void getRepository_beforeInitialization_throwsWithClearMessage() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TestJpaDal().getRepository())
            .withMessageContaining("has not been initialized");
    }

    @Test
    void getEntityType_beforeInitialization_throwsWithClearMessage() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TestJpaDal().getEntityType())
            .withMessageContaining("has not been initialized");
    }

    @Test
    void executeRead_withFilterBeforeSpecificationConverterSet_throwsWithClearMessage() {
        TestJpaDal dal = new TestJpaDal(repository, entityManager);
        FilterNode filter = mock(FilterNode.class);
        Pageable pageable = PageRequest.of(0, 10);

        assertThatNullPointerException()
            .isThrownBy(() -> dal.executeRead(filter, pageable))
            .withMessageContaining("FilterSpecificationConverter has not been initialized");
    }
}
