package com.paganbit.telaio.mongo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.exception.DalInvalidSortException;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.core.transaction.DefaultDalTransactionPolicy;
import com.paganbit.telaio.core.transaction.PassThroughTransactionManager;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterQueryConverter;
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
import org.springframework.context.annotation.Import;
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
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
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

    @Autowired
    private DalTransactionPolicy dalTransactionPolicy;

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
        // Standalone mongod: no real transactions — the pass-through manager lets read() run end-to-end.
        target.setTransactionManager(new PassThroughTransactionManager());
        target.setTransactionPolicy(dalTransactionPolicy);
    }

    private TestEntity persisted(String name) {
        return persisted(name, null, null);
    }

    private TestEntity persisted(String name, String label, String city) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setLabel(label);
        if (city != null) {
            Address address = new Address();
            address.setCity(city);
            entity.setAddress(address);
        }
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
        FilterQueryConverter exprConverter = mock(FilterQueryConverter.class);
        FilterNode filter = mock(FilterNode.class);
        doReturn(new BasicQuery("{\"$expr\": {\"$eq\": [\"$name\", \"alpha\"]}}"))
            .when(exprConverter).convert(filter, TestEntity.class);
        dal.setQueryConverter(exprConverter);

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
        FilterQueryConverter alphaOnly = mock(FilterQueryConverter.class);
        doReturn(new BasicQuery("{\"$expr\": {\"$eq\": [\"$name\", \"alpha\"]}}"))
            .when(alphaOnly).convert(defaultFilter, TestEntity.class);
        filteredDal.setQueryConverter(alphaOnly);
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

    @Test
    void read_withJsonWireNameSort_ordersLikeTheJavaName() {
        persisted("beta", "z", "Zagreb");
        persisted("alpha", "a", "Ancona");

        Page<TestEntity> byWireName = dal.read(null, PageRequest.of(0, 10, Sort.by("display_name")));
        Page<TestEntity> byJavaName = dal.read(null, PageRequest.of(0, 10, Sort.by("label")));

        assertThat(byWireName.getContent()).extracting(TestEntity::getLabel).containsExactly("a", "z");
        assertThat(byJavaName.getContent()).extracting(TestEntity::getLabel).containsExactly("a", "z");
    }

    @Test
    void read_withNestedSortPath_ordersThroughTheSubdocument() {
        persisted("beta", "z", "Zagreb");
        persisted("alpha", "a", "Ancona");

        Page<TestEntity> page = dal.read(
            null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "address.city")));

        assertThat(page.getContent()).extracting(TestEntity::getName).containsExactly("beta", "alpha");
    }

    @Test
    void read_withUnknownSortProperty_isRejectedAsClientFault() {
        // Mongo used to sort on the missing path silently (200 with an arbitrary order).
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("nope"));

        assertThatThrownBy(() -> dal.read(null, pageable))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("nope");
    }

    @Test
    void read_withSerializedButNotPersistedSortProperty_isRejectedAsClientFault() {
        // `display` is a computed getter: Jackson serializes it, the mapping context does not store it.
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("display"));

        assertThatThrownBy(() -> dal.read(null, pageable))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("display");
    }

    @Test
    void read_withUnsortedPageable_appliesTheDefaultSortWithoutValidation() {
        TestEntity first = persisted("beta");
        TestEntity second = persisted("alpha");

        Page<TestEntity> page = dal.read(null, PageRequest.of(0, 10));

        // Default sort is id ascending: ObjectId-backed String ids are monotonic within the process.
        assertThat(page.getContent()).extracting(TestEntity::getId)
            .containsExactly(first.getId(), second.getId());
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

        @JsonProperty("display_name")
        private String label;

        private Address address;

        /**
         * Serialized by Jackson but not stored by the mapping context: the sort validation must reject it.
         */
        public String getDisplay() {
            return name + "!";
        }
    }

    @Getter
    @Setter
    static class Address {

        private String city;
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
    @Import(DefaultDalTransactionPolicy.class)
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
