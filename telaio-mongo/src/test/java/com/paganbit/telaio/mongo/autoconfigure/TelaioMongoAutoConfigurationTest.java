package com.paganbit.telaio.mongo.autoconfigure;

import com.paganbit.telaio.core.transaction.PassThroughTransactionManager;
import com.paganbit.telaio.introspection.SimpleTypeContributor;
import com.paganbit.telaio.mongo.MongoDal;
import com.paganbit.telaio.mongo.filter.FilterQueryConverter;
import com.paganbit.telaio.mongo.filter.JsonAwareFilterQueryConverter;
import com.paganbit.telaio.mongo.filter.ObjectIdAwareFieldTypeResolver;
import com.paganbit.telaio.mongo.jackson.ObjectIdJacksonModule;
import com.turkraft.springfilter.helper.FieldTypeResolver;
import com.turkraft.springfilter.helper.JsonNodeHelper;
import com.turkraft.springfilter.transformer.FilterJsonNodeTransformer;
import com.turkraft.springfilter.transformer.processor.factory.FilterNodeProcessorFactories;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link ApplicationContextRunner} tests for {@link TelaioMongoAutoConfiguration}: both beans are
 * additive and overridable, the transaction-manager fallback prefers a user-declared
 * {@link MongoTransactionManager}, and the whole autoconfiguration backs off when the Turkraft
 * mongo artifact is not on the classpath.
 */
class TelaioMongoAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TelaioMongoAutoConfiguration.class));

    private ApplicationContextRunner withTurkraftMongoBeans() {
        return runner
            .withBean(FilterNodeProcessorFactories.class, () -> mock(FilterNodeProcessorFactories.class))
            .withBean(FieldTypeResolver.class, () -> mock(FieldTypeResolver.class))
            .withBean(JsonNodeHelper.class, () -> mock(JsonNodeHelper.class));
    }

    @Test
    void converter_registeredWhenTurkraftMongoBeansPresent() {
        withTurkraftMongoBeans().run(context -> {
            assertThat(context).hasSingleBean(FilterQueryConverter.class);
            assertThat(context.getBean(FilterQueryConverter.class))
                .isInstanceOf(JsonAwareFilterQueryConverter.class);
        });
    }

    @Test
    void converter_backsOffWithoutTurkraftMongoBeans() {
        runner.run(context -> assertThat(context).doesNotHaveBean(FilterQueryConverter.class));
    }

    @Test
    void converter_backsOffWhenUserDefinesOne() {
        FilterQueryConverter custom = mock(FilterQueryConverter.class);
        withTurkraftMongoBeans()
            .withBean("customFilterQueryConverter", FilterQueryConverter.class, () -> custom)
            .run(context -> {
                assertThat(context).hasSingleBean(FilterQueryConverter.class);
                assertThat(context.getBean(FilterQueryConverter.class)).isSameAs(custom);
            });
    }

    @Test
    void transactionManager_fallsBackToPassThrough() {
        runner.run(context -> {
            assertThat(context).hasBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME);
            assertThat(context.getBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME))
                .isInstanceOf(PassThroughTransactionManager.class);
        });
    }

    @Test
    void transactionManager_prefersUserDeclaredMongoTransactionManager() {
        MongoTransactionManager userDeclared =
            new MongoTransactionManager(mock(MongoDatabaseFactory.class));
        runner
            .withBean(MongoTransactionManager.class, () -> userDeclared)
            .run(context ->
                assertThat(context.getBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME)).isSameAs(userDeclared));
    }

    @Test
    void transactionManager_multipleMongoTransactionManagers_fallsBackToPassThrough() {
        runner
            .withBean("firstManager", MongoTransactionManager.class, () ->
                new MongoTransactionManager(mock(MongoDatabaseFactory.class)))
            .withBean("secondManager", MongoTransactionManager.class, () ->
                new MongoTransactionManager(mock(MongoDatabaseFactory.class)))
            .run(context ->
                assertThat(context.getBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME))
                    .isInstanceOf(PassThroughTransactionManager.class));
    }

    @Test
    void transactionManager_backsOffWhenUserDefinesQualifiedBean() {
        PlatformTransactionManager custom = mock(PlatformTransactionManager.class);
        runner
            .withBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME, PlatformTransactionManager.class, () -> custom)
            .run(context ->
                assertThat(context.getBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME)).isSameAs(custom));
    }

    /**
     * Mixed-backend scenario: another backend's manager (bean name {@code transactionManager}, as
     * Boot names the JPA one) coexists with a user-declared {@code MongoTransactionManager}. The
     * qualified mongo bean must resolve to the user's manager, while a plain by-type injection
     * point with a {@code transactionManager} parameter (what {@code AbstractDal} declares) must
     * still resolve unambiguously to the other backend's manager.
     */
    @Test
    void transactionManager_mixedBackendContext_staysUnambiguous() {
        MongoTransactionManager userDeclared =
            new MongoTransactionManager(mock(MongoDatabaseFactory.class));
        PlatformTransactionManager otherBackend = mock(PlatformTransactionManager.class);
        runner
            .withBean("transactionManager", PlatformTransactionManager.class, () -> otherBackend)
            .withBean(MongoTransactionManager.class, () -> userDeclared)
            .withUserConfiguration(ByTypeConsumer.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME)).isSameAs(userDeclared);
                assertThat(context.getBean(ByTypeConsumer.class).transactionManager).isSameAs(otherBackend);
            });
    }

    static class ByTypeConsumer {

        PlatformTransactionManager transactionManager;

        @Autowired
        public void setTransactionManager(PlatformTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
        }
    }

    @Test
    void objectIdJacksonModule_registered() {
        runner.run(context -> assertThat(context).hasSingleBean(ObjectIdJacksonModule.class));
    }

    @Test
    void objectIdJacksonModule_backsOffWhenUserDefinesOne() {
        ObjectIdJacksonModule custom = new ObjectIdJacksonModule();
        runner
            .withBean("customObjectIdJacksonModule", ObjectIdJacksonModule.class, () -> custom)
            .run(context -> assertThat(context.getBean(ObjectIdJacksonModule.class)).isSameAs(custom));
    }

    @Test
    void simpleTypeContributor_contributesObjectId() {
        runner.run(context ->
            assertThat(context.getBean(SimpleTypeContributor.class).simpleTypes())
                .containsExactly(ObjectId.class));
    }

    @Test
    void fieldTypeResolver_primaryDecoratesTurkraftResolver() {
        withTurkraftMongoBeans().run(context ->
            assertThat(context.getBean(FieldTypeResolver.class))
                .isInstanceOf(ObjectIdAwareFieldTypeResolver.class));
    }

    @Test
    void autoConfiguration_backsOffWithoutTurkraftMongoOnClasspath() {
        runner
            .withClassLoader(new FilteredClassLoader(FilterJsonNodeTransformer.class))
            .run(context -> {
                assertThat(context).doesNotHaveBean(TelaioMongoAutoConfiguration.class);
                assertThat(context).doesNotHaveBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME);
            });
    }
}
