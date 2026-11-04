package com.paganbit.telaio.core.autoconfigure;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationAdapterAssembler;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.channel.DalChannelDefinition;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import com.paganbit.telaio.core.registry.DalCatalog;
import com.paganbit.telaio.core.registry.DalManager;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.paganbit.telaio.core.adapter.DalOperationAdapterRegistry;
import com.paganbit.telaio.core.adapter.InMemoryDalOperationAdapterRegistry;
import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.beans.DefaultDalPropertyMerger;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.core.transaction.DefaultDalTransactionPolicy;
import com.paganbit.telaio.core.validation.DalValidator;
import com.paganbit.telaio.core.validation.DefaultDalValidator;
import com.paganbit.telaio.core.version.TelaioVersionProvider;
import com.paganbit.telaio.introspection.DefaultSimpleTypePredicate;
import com.paganbit.telaio.introspection.SimpleTypeContributor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class TelaioCoreAutoConfigurationTest {

    private final ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class, TelaioCoreAutoConfiguration.class))
        .withBean(SpringValidatorAdapter.class,
            () -> new SpringValidatorAdapter(mock(jakarta.validation.Validator.class)))
        .withUserConfiguration(BasicConfig.class);

    @Test
    void shouldProvideTheSharedPathResolver() {
        applicationContextRunner.run(context ->
            assertThat(context).hasSingleBean(JsonPropertyPathResolver.class));
    }

    @Test
    void shouldProvideTheSharedDalValidator() {
        applicationContextRunner.run(context -> {
            assertThat(context).hasSingleBean(DalValidator.class);
            assertThat(context.getBean(DalValidator.class)).isInstanceOf(DefaultDalValidator.class);
        });
    }

    @Test
    void withoutAnObjectMapperBean_theContextFailsToStart() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TelaioCoreAutoConfiguration.class))
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldProvideDefaultDalBeans() {
        applicationContextRunner.run(context -> {
            assertEquals(1, context.getBeansOfType(DalPropertyMerger.class).size());
            assertEquals(1, context.getBeansOfType(DalTransactionPolicy.class).size());

            DalPropertyMerger merger = context.getBean(DalPropertyMerger.class);
            assertNotNull(merger);
            assertEquals(DefaultDalPropertyMerger.class, merger.getClass());

            DalTransactionPolicy policy = context.getBean(DalTransactionPolicy.class);
            assertNotNull(policy);
            assertEquals(DefaultDalTransactionPolicy.class, policy.getClass());

            assertThat(context).hasSingleBean(DalPropertyMerger.class);
            assertThat(context).hasSingleBean(DalTransactionPolicy.class);
        });
    }

    @Test
    void shouldProvideDefaultTelaioVersionProvider() {
        applicationContextRunner.run(context ->
            assertThat(context).hasSingleBean(TelaioVersionProvider.class));
    }

    @Test
    void shouldBackOffTelaioVersionProviderIfCustomBeanPresent() {
        applicationContextRunner
            .withBean("customVersionProvider", TelaioVersionProvider.class, TelaioVersionProvider::new)
            .run(context -> {
                assertEquals(1, context.getBeansOfType(TelaioVersionProvider.class).size());
                assertEquals("customVersionProvider", context.getBeanNamesForType(TelaioVersionProvider.class)[0]);
            });
    }

    @Test
    void shouldBackOffIfCustomBeansPresent() {
        applicationContextRunner
            .withUserConfiguration(CustomConfiguration.class)
            .run(context -> {
                assertEquals(1, context.getBeansOfType(DalTransactionPolicy.class).size());
                assertEquals(1, context.getBeansOfType(DalPropertyMerger.class).size());

                assertEquals("customDalTransactionPolicy", context.getBeanNamesForType(DalTransactionPolicy.class)[0]);
                assertEquals("customDalPropertyMerger", context.getBeanNamesForType(DalPropertyMerger.class)[0]);
            });
    }

    @Test
    void simpleTypePredicate_aggregatesContributions() {
        applicationContextRunner
            .withBean(SimpleTypeContributor.class, () -> () -> Set.of(ContributedType.class))
            .run(context -> {
                DefaultSimpleTypePredicate predicate = context.getBean(DefaultSimpleTypePredicate.class);
                assertThat(predicate.test(ContributedType.class)).isTrue();
            });
    }

    @Test
    void simpleTypePredicate_withoutContributions_keepsDefaultClassification() {
        applicationContextRunner.run(context -> {
            DefaultSimpleTypePredicate predicate = context.getBean(DefaultSimpleTypePredicate.class);
            assertThat(predicate.test(String.class)).isTrue();
            assertThat(predicate.test(ContributedType.class)).isFalse();
        });
    }

    @Test
    void simpleTypePredicate_backsOffWhenUserDefinesOne() {
        DefaultSimpleTypePredicate custom = new DefaultSimpleTypePredicate();
        applicationContextRunner
            .withBean("customSimpleTypePredicate", DefaultSimpleTypePredicate.class, () -> custom)
            .run(context ->
                assertThat(context.getBean(DefaultSimpleTypePredicate.class)).isSameAs(custom));
    }

    @Test
    void shouldProvideOperationAdapterAssembly() {
        applicationContextRunner.run(context -> {
            assertThat(context).hasSingleBean(DalOperationAdapterRegistry.class);
            assertThat(context.getBean(DalOperationAdapterRegistry.class))
                .isInstanceOf(InMemoryDalOperationAdapterRegistry.class);
            assertThat(context).hasSingleBean(DalOperationAdapterAssembler.class);
        });
    }

    @Test
    void customOperationAdapterRegistry_shouldReplaceDefault() {
        applicationContextRunner
            .withBean(DalOperationAdapterRegistry.class, () -> mock(DalOperationAdapterRegistry.class))
            .run(context -> {
                assertThat(context).hasSingleBean(DalOperationAdapterRegistry.class);
                assertThat(context).doesNotHaveBean(InMemoryDalOperationAdapterRegistry.class);
            });
    }

    @Test
    void channelDefinitionBean_shouldGetAnAdapterChainPerDal() {
        applicationContextRunner
            .withBean("testChannel", DalChannelDefinition.class, ExposeAllChannel::new)
            .withBean("widgets", WidgetDal.class, WidgetDal::new)
            .run(context -> {
                DalOperationAdapter<Object> adapter =
                    context.getBean(DalOperationAdapterRegistry.class).get(TEST_CHANNEL, "widgets");
                assertThat(adapter.create(Map.of())).isSameAs(WidgetDal.CREATED);
            });
    }

    @Test
    void withoutChannelDefinition_shouldAssembleNoChain() {
        applicationContextRunner
            .withBean("widgets", WidgetDal.class, WidgetDal::new)
            .run(context -> {
                DalOperationAdapterRegistry registry = context.getBean(DalOperationAdapterRegistry.class);
                assertThatThrownBy(() -> registry.get(TEST_CHANNEL, "widgets"))
                    .isInstanceOf(DalNotFoundException.class);
            });
    }

    @Test
    void dalCatalog_shouldResolveToTheDalManager() {
        applicationContextRunner.run(context ->
            assertThat(context.getBean(DalCatalog.class)).isSameAs(context.getBean(DalManager.class)));
    }

    private static final DalChannel TEST_CHANNEL = DalChannel.of("test");

    static class ExposeAllChannel implements DalChannelDefinition {

        @Override
        public DalChannel channel() {
            return TEST_CHANNEL;
        }

        @Override
        public Set<DalOperationType> exposedOperations(Class<?> dalClass) {
            return EnumSet.allOf(DalOperationType.class);
        }
    }

    @DalService(name = "widgets")
    static class WidgetDal implements Dal<Object, Long> {

        static final Object CREATED = new Object();

        @Override
        public Object create(Map<String, Object> properties) {
            return CREATED;
        }

        @Override
        public Page<Object> read(@Nullable FilterNode filter, Pageable pageable) {
            return Page.empty();
        }

        @Override
        public Optional<Object> readOne(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<Object> update(Long id, Map<String, Object> properties) {
            return Optional.empty();
        }

        @Override
        public void delete(Long id) {
            //noop
        }

        @Override
        public Class<Object> getEntityClass() {
            return Object.class;
        }

        @Override
        public Class<Long> getIdClass() {
            return Long.class;
        }
    }

    private static final class ContributedType {
    }

    static class BasicConfig {

        @Bean(name = "sfConversionService")
        ConversionService conversionService() {
            return new DefaultConversionService();
        }
    }

    static class CustomConfiguration {

        @Bean("customDalTransactionPolicy")
        DalTransactionPolicy dalTransactionPolicy() {
            return mock(DalTransactionPolicy.class);
        }

        @Bean("customDalPropertyMerger")
        DalPropertyMerger dalPropertyMerger() {
            return mock(DalPropertyMerger.class);
        }
    }
}
