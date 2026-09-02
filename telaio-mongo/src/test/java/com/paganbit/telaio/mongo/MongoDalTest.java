package com.paganbit.telaio.mongo;

import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.exception.DalEntityNotFoundException;
import com.paganbit.telaio.core.json.JsonFieldNameSortRewriter;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.core.validation.DefaultDalValidator;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import jakarta.validation.Validator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link MongoDal}'s setter-based wiring and {@link MongoDal#afterPropertiesSet()}
 * lifecycle. The Spring-managed end-to-end path (component scanning, generic-aware repository
 * autowiring, CGLIB interception) is not exercised here; repository-and-template behavior against a
 * real server is covered by {@code MongoDalIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoDalTest {

    static class TestEntity {
    }

    /**
     * Empty-body subclass mirroring the way concrete DALs are declared in a Spring context.
     */
    static class TestMongoDal extends MongoDal<TestEntity, String> {

        TestMongoDal() {
            super();
        }

        TestMongoDal(MongoDalRepository<TestEntity, String> repository, MongoOperations mongoOperations) {
            super(repository, mongoOperations);
        }
    }

    /**
     * Subclass exposing a non-null {@code defaultFilter()} to exercise query composition.
     */
    static class DefaultFilteredMongoDal extends TestMongoDal {

        private final FilterNode defaultFilter;

        DefaultFilteredMongoDal(
            MongoDalRepository<TestEntity, String> repository,
            MongoOperations mongoOperations,
            FilterNode defaultFilter
        ) {
            super(repository, mongoOperations);
            this.defaultFilter = defaultFilter;
        }

        @Override
        protected FilterNode defaultFilter() {
            return defaultFilter;
        }
    }

    @Mock
    private MongoDalRepository<TestEntity, String> repository;

    @Mock
    private MongoOperations mongoOperations;

    @Mock
    private MongoConverter mongoConverter;

    @SuppressWarnings("rawtypes")
    @Mock
    private MappingContext mappingContext;

    @Mock
    private MongoPersistentEntity<TestEntity> persistentEntity;

    @Mock
    private Validator validator;

    @Mock
    private DalPropertyMerger propertyMerger;

    @Mock
    private FilterBuilder filterBuilder;

    @Mock
    private FilterStringConverter filterStringConverter;

    @Mock
    private FilterQueryConverter queryConverter;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private DalTransactionPolicy transactionPolicy;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpMappingContext() {
        lenient().doReturn(mongoConverter).when(mongoOperations).getConverter();
        lenient().doReturn(mappingContext).when(mongoConverter).getMappingContext();
        lenient().doReturn(persistentEntity).when(mappingContext).getRequiredPersistentEntity(TestEntity.class);
    }

    /**
     * Injects the {@link com.paganbit.telaio.core.AbstractDal} collaborators required by init.
     */
    private void wireAbstractDalCollaborators(TestMongoDal dal) {
        dal.setObjectMapper(objectMapper);
        JsonPropertyPathResolver pathResolver = new JsonPropertyPathResolver(objectMapper);
        dal.setSortRewriter(new JsonFieldNameSortRewriter(pathResolver));
        dal.setDalValidator(new DefaultDalValidator(new SpringValidatorAdapter(validator), pathResolver));
        dal.setPropertyMerger(propertyMerger);
        dal.setFilterBuilder(filterBuilder);
        dal.setFilterStringConverter(filterStringConverter);
        dal.setTransactionManager(transactionManager);
        dal.setTransactionPolicy(transactionPolicy);
    }

    /**
     * Returns a fully-initialized DAL (constructor + collaborators + query converter + init).
     */
    private TestMongoDal readyDal() {
        TestMongoDal dal = new TestMongoDal(repository, mongoOperations);
        wireAbstractDalCollaborators(dal);
        dal.setQueryConverter(queryConverter);
        dal.afterPropertiesSet();
        return dal;
    }

    @Test
    void afterPropertiesSet_viaConstructor_computesPersistentEntityAndExposesGetters() {
        TestMongoDal dal = new TestMongoDal(repository, mongoOperations);
        wireAbstractDalCollaborators(dal);
        dal.setQueryConverter(queryConverter);

        dal.afterPropertiesSet();

        assertThat(dal.getEntityClass()).isEqualTo(TestEntity.class);
        assertThat(dal.getIdClass()).isEqualTo(String.class);
        assertThat(dal.getRepository()).isSameAs(repository);
        assertThat(dal.getPersistentEntity()).isSameAs(persistentEntity);
    }

    @Test
    void afterPropertiesSet_viaSetters_computesPersistentEntityAndExposesGetters() {
        TestMongoDal dal = new TestMongoDal();
        dal.setRepository(repository);
        dal.setMongoOperations(mongoOperations);
        wireAbstractDalCollaborators(dal);

        dal.afterPropertiesSet();

        assertThat(dal.getRepository()).isSameAs(repository);
        assertThat(dal.getPersistentEntity()).isSameAs(persistentEntity);
    }

    @Test
    void afterPropertiesSet_missingRepository_throwsWithClearMessage() {
        TestMongoDal dal = new TestMongoDal();
        dal.setMongoOperations(mongoOperations);
        wireAbstractDalCollaborators(dal);

        assertThatNullPointerException()
            .isThrownBy(dal::afterPropertiesSet)
            .withMessageContaining("MongoDalRepository")
            .withMessageContaining(TestEntity.class.getSimpleName());
    }

    @Test
    void afterPropertiesSet_missingMongoOperations_throwsWithClearMessage() {
        TestMongoDal dal = new TestMongoDal();
        dal.setRepository(repository);
        wireAbstractDalCollaborators(dal);

        assertThatNullPointerException()
            .isThrownBy(dal::afterPropertiesSet)
            .withMessageContaining("MongoOperations");
    }

    @Test
    void setRepository_null_throws() {
        TestMongoDal dal = new TestMongoDal();
        assertThatNullPointerException().isThrownBy(() -> dal.setRepository(null));
    }

    @Test
    void setMongoOperations_null_throws() {
        TestMongoDal dal = new TestMongoDal();
        assertThatNullPointerException().isThrownBy(() -> dal.setMongoOperations(null));
    }

    @Test
    void setQueryConverter_null_throws() {
        TestMongoDal dal = new TestMongoDal();
        assertThatNullPointerException().isThrownBy(() -> dal.setQueryConverter(null));
    }

    // -----------------------------------------------------------------------------------------------
    // CRUD delegation to the repository / template
    // -----------------------------------------------------------------------------------------------

    @Test
    void executeCreate_delegatesToRepositorySave() {
        TestMongoDal dal = readyDal();
        TestEntity entity = new TestEntity();
        TestEntity saved = new TestEntity();
        doReturn(saved).when(repository).save(entity);

        assertThat(dal.executeCreate(entity)).isSameAs(saved);
        verify(repository).save(entity);
    }

    @Test
    void executeUpdate_delegatesToRepositorySave() {
        TestMongoDal dal = readyDal();
        TestEntity entity = new TestEntity();
        TestEntity saved = new TestEntity();
        doReturn(saved).when(repository).save(entity);

        assertThat(dal.executeUpdate(entity)).isSameAs(saved);
        verify(repository).save(entity);
    }

    @Test
    void executeDelete_delegatesToRepositoryInstanceDelete() {
        TestMongoDal dal = readyDal();
        TestEntity entity = new TestEntity();

        dal.executeDelete(entity);

        // Instance delete (not deleteById): honors @Version when the entity declares one.
        verify(repository).delete(entity);
    }

    @Test
    void executeRead_withoutFilter_usesPageableOverloadAndSkipsConverter() {
        TestMongoDal dal = readyDal();
        Pageable pageable = PageRequest.of(0, 10);
        Page<TestEntity> page = new PageImpl<>(List.of(new TestEntity()));
        doReturn(page).when(repository).findAll(pageable);

        assertThat(dal.executeRead(null, pageable)).isSameAs(page);
        verify(repository).findAll(pageable);
        verifyNoInteractions(queryConverter);
        verify(mongoOperations, never()).find(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void executeRead_withFilter_convertsAndQueriesViaMongoOperations() {
        TestMongoDal dal = readyDal();
        Pageable pageable = PageRequest.of(0, 10);
        FilterNode filter = mock(FilterNode.class);
        TestEntity entity = new TestEntity();
        Query converted = new BasicQuery(new Document("$expr", new Document("$eq", List.of("$name", "alpha"))));
        doReturn(converted).when(queryConverter).convert(filter, TestEntity.class);
        doReturn(List.of(entity)).when(mongoOperations).find(any(Query.class), eq(TestEntity.class));

        Page<TestEntity> page = dal.executeRead(filter, pageable);

        assertThat(page.getContent()).containsExactly(entity);
        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(queryConverter).convert(filter, TestEntity.class);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoOperations).find(queryCaptor.capture(), eq(TestEntity.class));
        assertThat(queryCaptor.getValue().getLimit()).isEqualTo(10);
        assertThat(queryCaptor.getValue().getSkip()).isZero();
        // The page is not full, so PageableExecutionUtils derives the total without a count query.
        verify(mongoOperations, never()).count(any(Query.class), eq(TestEntity.class));
    }

    @Test
    void executeReadOne_queriesByRawIdField() {
        TestMongoDal dal = readyDal();
        TestEntity entity = new TestEntity();
        doReturn(entity).when(mongoOperations).findOne(any(Query.class), eq(TestEntity.class));

        assertThat(dal.executeReadOne("42")).contains(entity);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoOperations).findOne(queryCaptor.capture(), eq(TestEntity.class));
        assertThat(queryCaptor.getValue().getQueryObject()).containsEntry("_id", "42");
    }

    // -----------------------------------------------------------------------------------------------
    // Default-filter query composition
    // -----------------------------------------------------------------------------------------------

    @Test
    void combineWithDefaultQuery_noDefaultFilter_returnsSameQuery() {
        TestMongoDal dal = readyDal();
        Query input = new Query(Criteria.where("_id").is("42"));

        assertThat(dal.combineWithDefaultQuery(input)).isSameAs(input);
        verifyNoInteractions(queryConverter);
    }

    @Test
    void combineWithDefaultQuery_withDefaultFilter_composesTopLevelAnd() {
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredMongoDal dal = filteredDal(defaultFilter);
        Query defaultQuery = new BasicQuery(new Document("$expr", new Document("$eq", List.of("$name", "alpha"))));
        doReturn(defaultQuery).when(queryConverter).convert(defaultFilter, TestEntity.class);
        Query input = new Query(Criteria.where("_id").is("42"));

        Query result = dal.combineWithDefaultQuery(input);

        assertThat(result).isNotSameAs(input);
        assertThat(result.getQueryObject().get("$and"))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
            .containsExactly(input.getQueryObject(), defaultQuery.getQueryObject());
        verify(queryConverter).convert(defaultFilter, TestEntity.class);
    }

    // -----------------------------------------------------------------------------------------------
    // Delete honors the default filter (by-id operations go through the filtered lookup)
    // -----------------------------------------------------------------------------------------------

    /**
     * Returns a fully initialized DAL whose {@code defaultFilter()} converts to the given query, so
     * the filtered by-id lookup is exercised end-to-end at the mock level.
     */
    private DefaultFilteredMongoDal filteredDal(FilterNode defaultFilter) {
        DefaultFilteredMongoDal dal = new DefaultFilteredMongoDal(repository, mongoOperations, defaultFilter);
        wireAbstractDalCollaborators(dal);
        dal.setQueryConverter(queryConverter);
        dal.afterPropertiesSet();
        return dal;
    }

    @Test
    void delete_entityHiddenByDefaultFilter_throwsNotFoundAndNeverDeletes() {
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredMongoDal dal = filteredDal(defaultFilter);
        doReturn(new BasicQuery(new Document("name", "alpha")))
            .when(queryConverter).convert(defaultFilter, TestEntity.class);
        // The filtered by-id lookup does not see the document: hidden behaves exactly like missing.
        // The pre-check runs inside the delete transaction, so the policy is consulted.
        when(mongoOperations.findOne(any(Query.class), eq(TestEntity.class))).thenReturn(null);
        when(transactionPolicy.forDelete()).thenReturn(new DefaultTransactionDefinition());

        assertThatExceptionOfType(DalEntityNotFoundException.class)
            .isThrownBy(() -> dal.delete("42"));

        verify(repository, never()).delete(any(TestEntity.class));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void update_entityHiddenByDefaultFilter_throwsNotFoundAndNeverSaves() {
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredMongoDal dal = filteredDal(defaultFilter);
        doReturn(new BasicQuery(new Document("name", "alpha")))
            .when(queryConverter).convert(defaultFilter, TestEntity.class);
        when(mongoOperations.findOne(any(Query.class), eq(TestEntity.class))).thenReturn(null);

        assertThatExceptionOfType(DalEntityNotFoundException.class)
            .isThrownBy(() -> dal.update("42", Map.of()));

        verify(repository, never()).save(any());
    }

    @Test
    void delete_visibleEntity_deletesTheLoadedInstance() {
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredMongoDal dal = filteredDal(defaultFilter);
        doReturn(new BasicQuery(new Document("name", "alpha")))
            .when(queryConverter).convert(defaultFilter, TestEntity.class);
        TestEntity loaded = new TestEntity();
        when(mongoOperations.findOne(any(Query.class), eq(TestEntity.class))).thenReturn(loaded);
        when(transactionPolicy.forDelete()).thenReturn(new DefaultTransactionDefinition());

        dal.delete("42");

        verify(mongoOperations).findOne(any(Query.class), eq(TestEntity.class));
        // The very instance returned by the filtered lookup is removed (instance delete).
        verify(repository).delete(loaded);
    }

    // -----------------------------------------------------------------------------------------------
    // Guarded accessors before initialization
    // -----------------------------------------------------------------------------------------------

    @Test
    void getRepository_beforeInitialization_throwsWithClearMessage() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TestMongoDal().getRepository())
            .withMessageContaining("has not been initialized");
    }

    @Test
    void getPersistentEntity_beforeInitialization_throwsWithClearMessage() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TestMongoDal().getPersistentEntity())
            .withMessageContaining("has not been initialized");
    }

    @Test
    void executeReadOne_beforeMongoOperationsSet_throwsWithClearMessage() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TestMongoDal().executeReadOne("42"))
            .withMessageContaining("MongoOperations has not been initialized");
    }

    @Test
    void executeRead_withFilterBeforeQueryConverterSet_throwsWithClearMessage() {
        TestMongoDal dal = new TestMongoDal(repository, mongoOperations);
        FilterNode filter = mock(FilterNode.class);
        Pageable pageable = PageRequest.of(0, 10);

        assertThatNullPointerException()
            .isThrownBy(() -> dal.executeRead(filter, pageable))
            .withMessageContaining("FilterQueryConverter has not been initialized");
    }
}
