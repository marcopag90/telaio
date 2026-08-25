package com.paganbit.telaio.mongo;

import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.mongo.filter.FilterQueryConverter;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link MongoDal} behavior that requires real Spring Data Mongo mapping
 * metadata, repository and template against a live server (Testcontainers, standalone
 * {@code mongod} — transactions intentionally unavailable; the transactional paths are covered by
 * {@link MongoDalTransactionIntegrationTest}): {@link MongoDal#defaultSort()},
 * {@link MongoDal#executeReadOne(Object)} (raw {@code _id} lookup and {@code $and} composition with
 * the default filter), {@link MongoDal#executeRead}
 * (including an {@code $expr} query with count + paging — the exact query shape
 * {@code JsonAwareFilterQueryConverter} produces), and the version-checked instance delete. The DAL
 * is hand-built with a real repository + {@link MongoOperations} and mocked {@code AbstractDal}
 * collaborators (only null-checked on the paths under test), avoiding the cost of booting a full
 * Telaio application context.
 */
@DataMongoTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = MongoDalIntegrationTest.TestConfig.class)
class MongoDalIntegrationTest {

    @Autowired
    private MongoOperations mongoOperations;

    @Autowired
    private TestConfig.TestEntityRepository repository;

    @Autowired
    private TestConfig.VersionedEntityRepository versionedRepository;

    @Autowired
    private TestConfig.ObjectIdEntityRepository objectIdRepository;

    private TestMongoDal dal;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        versionedRepository.deleteAll();
        objectIdRepository.deleteAll();
        dal = new TestMongoDal(repository, mongoOperations);
        wireAbstractDalCollaborators(dal);
        dal.afterPropertiesSet();
    }

    private void wireAbstractDalCollaborators(MongoDal<?, ?> target) {
        target.setObjectMapper(JsonMapper.builder().build());
        target.setValidatorAdapter(new SpringValidatorAdapter(mock(Validator.class)));
        target.setPropertyMerger(mock(DalPropertyMerger.class));
        target.setFilterBuilder(mock(FilterBuilder.class));
        target.setFilterStringConverter(mock(FilterStringConverter.class));
        target.setTransactionManager(mock(PlatformTransactionManager.class));
        target.setTransactionPolicy(mock(DalTransactionPolicy.class));
    }

    private TestEntity persisted(String name) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        return repository.save(entity);
    }

    @Test
    void defaultSort_resolvesIdBasedSortFromRealMappingContext() {
        Sort sort = dal.defaultSort();

        assertThat(sort.isSorted()).isTrue();
        assertThat(sort.getOrderFor("id")).isNotNull();
    }

    @Test
    void executeReadOne_findsPersistedEntityById() {
        TestEntity saved = persisted("alpha");

        Optional<TestEntity> found = dal.executeReadOne(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getName()).isEqualTo("alpha");
    }

    @Test
    void executeReadOne_returnsEmptyForUnknownId() {
        assertThat(dal.executeReadOne("000000000000000000000000")).isEmpty();
    }

    @Test
    void executeRead_withoutFilter_returnsAllPersistedRows() {
        persisted("a");
        persisted("b");

        Page<TestEntity> page = dal.executeRead(null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
    }

    /**
     * Runs the exact query shape {@code JsonAwareFilterQueryConverter} produces ({@code $expr}
     * aggregation expression) through {@code find} + {@code count} + paging against a real server —
     * proving Spring Data's query mapping, {@code countDocuments} and {@code Pageable} all accept it.
     */
    @Test
    void executeRead_withExprQuery_matchesCountsAndPages() {
        TestEntity firstAlpha = persisted("alpha");
        TestEntity secondAlpha = persisted("alpha");
        persisted("beta");
        FilterQueryConverter exprConverter =
            (node, type) -> new BasicQuery("{\"$expr\": {\"$eq\": [\"$name\", \"alpha\"]}}");
        dal.setQueryConverter(exprConverter);
        FilterNode filter = mock(FilterNode.class);

        // Page size 1 with 2 matches forces the count query to run against the $expr criteria.
        Page<TestEntity> page = dal.executeRead(filter, PageRequest.of(0, 1, Sort.by("id")));

        String expectedFirstId = firstAlpha.getId().compareTo(secondAlpha.getId()) <= 0
            ? firstAlpha.getId()
            : secondAlpha.getId();
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getId()).isEqualTo(expectedFirstId);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    /**
     * Proves the {@code $and} composition of {@code combineWithDefaultQuery} executes on a real
     * server: the by-id lookup only sees documents matching the default filter.
     */
    @Test
    void executeReadOne_withDefaultFilter_hidesNonMatchingDocument() {
        TestEntity visible = persisted("alpha");
        TestEntity hidden = persisted("beta");
        FilterNode defaultFilter = mock(FilterNode.class);
        DefaultFilteredMongoDal filteredDal = new DefaultFilteredMongoDal(repository, mongoOperations, defaultFilter);
        wireAbstractDalCollaborators(filteredDal);
        filteredDal.setQueryConverter(
            (node, type) -> new BasicQuery("{\"$expr\": {\"$eq\": [\"$name\", \"alpha\"]}}"));
        filteredDal.afterPropertiesSet();

        assertThat(filteredDal.executeReadOne(visible.getId())).isPresent();
        assertThat(filteredDal.executeReadOne(hidden.getId())).isEmpty();
    }

    /**
     * Proves the instance delete is version-checked even without transactions: deleting a stale
     * {@code @Version} snapshot fails instead of removing the concurrently-updated document.
     */
    @Test
    void executeDelete_staleVersion_throwsOptimisticLockingFailureException() {
        VersionedEntity saved = versionedRepository.save(new VersionedEntity());
        VersionedEntity stale = versionedRepository.findById(saved.getId()).orElseThrow();
        VersionedEntity concurrent = versionedRepository.findById(saved.getId()).orElseThrow();
        concurrent.setName("updated");
        versionedRepository.save(concurrent);

        VersionedMongoDal versionedDal = new VersionedMongoDal(versionedRepository, mongoOperations);
        wireAbstractDalCollaborators(versionedDal);
        versionedDal.afterPropertiesSet();

        assertThatExceptionOfType(OptimisticLockingFailureException.class)
            .isThrownBy(() -> versionedDal.executeDelete(stale));
        assertThat(versionedRepository.findById(saved.getId())).isPresent();
    }

    /**
     * Proves an {@code ObjectId}-typed identifier works through the DAL's own persistence paths:
     * create assigns an id, the raw {@code _id} lookup finds it, and the instance delete removes it.
     */
    @Test
    void objectIdIdentifier_persistsReadsAndDeletes() {
        ObjectIdMongoDal objectIdDal = new ObjectIdMongoDal(objectIdRepository, mongoOperations);
        wireAbstractDalCollaborators(objectIdDal);
        objectIdDal.afterPropertiesSet();
        ObjectIdEntity entity = new ObjectIdEntity();
        entity.setName("alpha");

        ObjectIdEntity saved = objectIdDal.executeCreate(entity);

        assertThat(saved.getId()).isNotNull();
        Optional<ObjectIdEntity> found = objectIdDal.executeReadOne(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("alpha");

        objectIdDal.executeDelete(found.get());
        assertThat(objectIdDal.executeReadOne(saved.getId())).isEmpty();
    }

    static class TestMongoDal extends MongoDal<TestEntity, String> {

        TestMongoDal(MongoDalRepository<TestEntity, String> repository, MongoOperations mongoOperations) {
            super(repository, mongoOperations);
        }
    }

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

    static class VersionedMongoDal extends MongoDal<VersionedEntity, String> {

        VersionedMongoDal(MongoDalRepository<VersionedEntity, String> repository, MongoOperations mongoOperations) {
            super(repository, mongoOperations);
        }
    }

    static class ObjectIdMongoDal extends MongoDal<ObjectIdEntity, ObjectId> {

        ObjectIdMongoDal(MongoDalRepository<ObjectIdEntity, ObjectId> repository, MongoOperations mongoOperations) {
            super(repository, mongoOperations);
        }
    }

    @Getter
    @Setter
    static class TestEntity {

        @Id
        private String id;

        private String name;
    }

    @Getter
    @Setter
    static class VersionedEntity {

        @Id
        private String id;

        private String name;

        @Version
        private Long version;
    }

    @Getter
    @Setter
    static class ObjectIdEntity {

        @Id
        private ObjectId id;

        private String name;
    }

    @EnableAutoConfiguration
    @EnableMongoRepositories(considerNestedRepositories = true)
    static class TestConfig {

        interface TestEntityRepository extends MongoDalRepository<TestEntity, String> {
        }

        interface VersionedEntityRepository extends MongoDalRepository<VersionedEntity, String> {
        }

        interface ObjectIdEntityRepository extends MongoDalRepository<ObjectIdEntity, ObjectId> {
        }

        @Bean
        @ServiceConnection
        MongoDBContainer standaloneMongoContainer() {
            return new MongoDBContainer(System.getProperty("testcontainers.image.mongodb", "mongo:8"));
        }
    }
}
