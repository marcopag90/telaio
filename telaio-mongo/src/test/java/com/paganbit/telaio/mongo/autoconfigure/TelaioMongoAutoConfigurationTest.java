package com.paganbit.telaio.mongo.autoconfigure;

import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.transaction.PassThroughTransactionManager;
import com.paganbit.telaio.introspection.SimpleTypeContributor;
import com.paganbit.telaio.mongo.MongoDal;
import com.paganbit.telaio.mongo.filter.JsonAwareFilterQueryConverter;
import com.paganbit.telaio.mongo.jackson.ObjectIdJacksonModule;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.converter.FilterQueryConverterImpl;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
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

    private final FilterQueryConverterImpl turkraftConverter = mock(FilterQueryConverterImpl.class);

    private ApplicationContextRunner withTurkraftMongoBeans() {
        return runner
            .withBean(FilterQueryConverterImpl.class, () -> turkraftConverter)
            .withBean(FilterStringConverter.class, () -> mock(FilterStringConverter.class))
            .withBean(JsonPropertyPathResolver.class,
                () -> new JsonPropertyPathResolver(JsonMapper.builder().build()))
            .withBean(MongoOperations.class, TelaioMongoAutoConfigurationTest::mongoOperations);
    }

    /**
     * A template whose converter exposes a real (empty) mapping context — the authority the
     * JSON-aware converter checks filtered fields against.
     */
    private static MongoOperations mongoOperations() {
        MongoConverter mongoConverter = mock(MongoConverter.class);
        doReturn(new MongoMappingContext()).when(mongoConverter).getMappingContext();
        MongoOperations mongoOperations = mock(MongoOperations.class);
        doReturn(mongoConverter).when(mongoOperations).getConverter();
        return mongoOperations;
    }

    @Test
    void converter_failsFastWithoutMongoOperations() {
        // The persistent-field check is not optional: without the template's mapping context the
        // decorator must not silently degrade to name-only validation.
        runner
            .withBean(FilterQueryConverterImpl.class, () -> turkraftConverter)
            .withBean(FilterStringConverter.class, () -> mock(FilterStringConverter.class))
            .withBean(JsonPropertyPathResolver.class,
                () -> new JsonPropertyPathResolver(JsonMapper.builder().build()))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(NoSuchBeanDefinitionException.class)
                    .hasMessageContaining(MongoOperations.class.getName());
            });
    }

    @Test
    void converter_decoratesTurkraftConverterAsPrimary() {
        withTurkraftMongoBeans().run(context -> {
            // Two FilterQueryConverter beans coexist: Turkraft's and the primary decorator.
            assertThat(context.getBeansOfType(FilterQueryConverter.class)).hasSize(2);
            assertThat(context.getBean(FilterQueryConverter.class))
                .isInstanceOf(JsonAwareFilterQueryConverter.class);
            assertThat(ReflectionTestUtils.getField(context.getBean(FilterQueryConverter.class), "delegate"))
                .isSameAs(turkraftConverter);
        });
    }

    @Test
    void converter_backsOffWithoutTurkraftConverter() {
        runner.run(context -> assertThat(context).doesNotHaveBean(FilterQueryConverter.class));
    }

    /**
     * A user-declared decorator replaces the autoconfigured one. Marked primary, as the contract requires:
     * Turkraft's own converter stays in the context, so a by-type injection point (what {@code MongoDal}
     * declares) must still resolve unambiguously.
     */
    @Test
    void converter_backsOffWhenUserDefinesTheDecorator() {
        JsonAwareFilterQueryConverter custom = new JsonAwareFilterQueryConverter(
            turkraftConverter, mock(FilterStringConverter.class),
            new JsonPropertyPathResolver(JsonMapper.builder().build()), new MongoMappingContext());
        withTurkraftMongoBeans()
            .withBean("customConverter", JsonAwareFilterQueryConverter.class, () -> custom,
                definition -> definition.setPrimary(true))
            .withUserConfiguration(ConverterByTypeConsumer.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(JsonAwareFilterQueryConverter.class);
                assertThat(context.getBean(JsonAwareFilterQueryConverter.class)).isSameAs(custom);
                assertThat(context.getBean(ConverterByTypeConsumer.class).converter).isSameAs(custom);
            });
    }

    /**
     * Any user-declared converter (not only the decorator type) makes the autoconfigured one back off;
     * Turkraft's own implementation is ignored by the guard, so the user bean must be primary.
     */
    @Test
    void converter_backsOffWhenUserDefinesAnyOtherConverter() {
        FilterQueryConverter custom = mock(FilterQueryConverter.class);
        withTurkraftMongoBeans()
            .withBean("customConverter", FilterQueryConverter.class, () -> custom,
                definition -> definition.setPrimary(true))
            .withUserConfiguration(ConverterByTypeConsumer.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(JsonAwareFilterQueryConverter.class);
                assertThat(context.getBean(FilterQueryConverter.class)).isSameAs(custom);
                assertThat(context.getBean(ConverterByTypeConsumer.class).converter).isSameAs(custom);
            });
    }

    static class ConverterByTypeConsumer {

        FilterQueryConverter converter;

        @Autowired
        public void setConverter(FilterQueryConverter converter) {
            this.converter = converter;
        }
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
    void autoConfiguration_backsOffWithoutTurkraftMongoOnClasspath() {
        runner
            .withClassLoader(new FilteredClassLoader(FilterQueryConverter.class))
            .run(context -> {
                assertThat(context).doesNotHaveBean(TelaioMongoAutoConfiguration.class);
                assertThat(context).doesNotHaveBean(MongoDal.TRANSACTION_MANAGER_BEAN_NAME);
            });
    }
}
