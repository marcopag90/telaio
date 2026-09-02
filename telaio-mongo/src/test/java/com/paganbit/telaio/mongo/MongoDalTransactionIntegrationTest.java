package com.paganbit.telaio.mongo;

import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.json.JsonFieldNameSortRewriter;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.transaction.DefaultDalTransactionPolicy;
import com.paganbit.telaio.core.validation.DefaultDalValidator;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterStringConverter;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link MongoDal} running under a real {@link MongoTransactionManager} against
 * a live single-node replica set. In Testcontainers 2.x the replica set is opt-in
 * ({@code MongoDBContainer.withReplicaSet()}) — a plain {@code MongoDBContainer} is a standalone
 * {@code mongod}, which rejects multi-document transactions at the server level. The tests prove the
 * transactional contract of the public {@code delete} operation: the document removed inside the
 * transaction is restored when a lifecycle hook fails afterwards, and gone once the transaction
 * commits. Non-transactional behavior is covered by {@link MongoDalIntegrationTest}, which
 * intentionally runs against a standalone server.
 */
@DataMongoTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = MongoDalTransactionIntegrationTest.TestConfig.class)
class MongoDalTransactionIntegrationTest {

    private static final String HOOK_FAILURE = "after-delete hook failed";

    @Autowired
    private MongoOperations mongoOperations;

    @Autowired
    private MongoDatabaseFactory mongoDatabaseFactory;

    @Autowired
    private TestConfig.TxEntityRepository repository;

    @BeforeEach
    void setUp() {
        // Fixtures are written outside any transaction (plain repository call).
        repository.deleteAll();
    }

    /**
     * Wires the {@code AbstractDal} collaborators: a real {@link MongoTransactionManager} bound to the
     * test's {@link MongoDatabaseFactory} plus real transaction definitions; everything the delete
     * path merely null-checks is mocked.
     */
    private void wireTransactionalCollaborators(MongoDal<?, ?> target) {
        final var mapper = JsonMapper.builder().build();
        final var pathResolver = new JsonPropertyPathResolver(mapper);
        target.setObjectMapper(mapper);
        target.setSortRewriter(new JsonFieldNameSortRewriter(pathResolver));
        target.setDalValidator(new DefaultDalValidator(
            new SpringValidatorAdapter(mock(Validator.class)), pathResolver));
        target.setPropertyMerger(mock(DalPropertyMerger.class));
        target.setFilterBuilder(mock(FilterBuilder.class));
        target.setFilterStringConverter(mock(FilterStringConverter.class));
        target.setTransactionManager(new MongoTransactionManager(mongoDatabaseFactory));
        target.setTransactionPolicy(new DefaultDalTransactionPolicy());
    }

    private TxEntity persisted(String name) {
        TxEntity entity = new TxEntity();
        entity.setName(name);
        return repository.save(entity);
    }

    @Test
    void delete_whenFinalizeAfterDeleteThrows_rollsBackTheRemove() {
        TxEntity saved = persisted("alpha");
        FailingAfterDeleteMongoDal dal = new FailingAfterDeleteMongoDal(repository, mongoOperations);
        wireTransactionalCollaborators(dal);
        dal.afterPropertiesSet();

        assertThatIllegalStateException()
            .isThrownBy(() -> dal.delete(saved.getId()))
            .withMessage(HOOK_FAILURE);

        assertThat(repository.findById(saved.getId()))
            .as("the removal ran inside the transaction, so the hook failure rolled it back")
            .isPresent();
    }

    @Test
    void delete_whenEveryHookSucceeds_commitsTheRemove() {
        TxEntity saved = persisted("beta");
        TxMongoDal dal = new TxMongoDal(repository, mongoOperations);
        wireTransactionalCollaborators(dal);
        dal.afterPropertiesSet();

        dal.delete(saved.getId());

        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    @Getter
    @Setter
    static class TxEntity {

        @Id
        private String id;

        private String name;
    }

    static class TxMongoDal extends MongoDal<TxEntity, String> {

        TxMongoDal(MongoDalRepository<TxEntity, String> repository, MongoOperations mongoOperations) {
            super(repository, mongoOperations);
        }
    }

    /**
     * Fails right after the document has been removed, while the transaction is still open.
     */
    static class FailingAfterDeleteMongoDal extends TxMongoDal {

        FailingAfterDeleteMongoDal(MongoDalRepository<TxEntity, String> repository, MongoOperations mongoOperations) {
            super(repository, mongoOperations);
        }

        @Override
        protected void finalizeAfterDelete(TxEntity entity) {
            throw new IllegalStateException(HOOK_FAILURE);
        }
    }

    @EnableAutoConfiguration
    @EnableMongoRepositories(considerNestedRepositories = true)
    static class TestConfig {

        interface TxEntityRepository extends MongoDalRepository<TxEntity, String> {
        }

        @Bean
        @ServiceConnection
        MongoDBContainer mongoContainer() {
            // withReplicaSet(): multi-document transactions need a replica set; Testcontainers 2.x
            // starts a standalone mongod unless asked otherwise. Image from the root pom's
            // testcontainers.image.mongodb surefire property, with a fallback for IDE runs.
            return new MongoDBContainer(System.getProperty("testcontainers.image.mongodb", "mongo:8"))
                .withReplicaSet();
        }
    }
}
