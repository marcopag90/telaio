package com.paganbit.telaio.core.autoconfigure;

import com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import com.paganbit.telaio.core.adapter.DalOperationAdapterAssembler;
import com.paganbit.telaio.core.adapter.DalOperationAdapterRegistry;
import com.paganbit.telaio.core.adapter.InMemoryDalOperationAdapterRegistry;
import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.beans.DefaultDalPropertyMerger;
import com.paganbit.telaio.core.beans.registration.DalDefinitionBeanPostProcessor;
import com.paganbit.telaio.core.beans.registration.DalFactoryPostProcessor;
import com.paganbit.telaio.core.beans.registration.DalInterceptionBeanPostProcessor;
import com.paganbit.telaio.core.channel.DalChannelDefinition;
import com.paganbit.telaio.core.interceptor.DalInterceptorProvider;
import com.paganbit.telaio.core.json.JsonFieldNameSortRewriter;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.core.registry.InMemoryDalManager;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.core.transaction.DefaultDalTransactionPolicy;
import com.paganbit.telaio.core.validation.DalValidator;
import com.paganbit.telaio.core.validation.DefaultDalValidator;
import com.paganbit.telaio.core.version.TelaioVersionProvider;
import com.paganbit.telaio.introspection.DefaultSimpleTypePredicate;
import com.paganbit.telaio.introspection.SimpleTypeContributor;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio Core
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@AutoConfiguration
public class TelaioCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DalPropertyMerger dalPropertyMerger(ObjectMapper objectMapper) {
        return new DefaultDalPropertyMerger(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    JsonPropertyPathResolver dalJsonPropertyPathResolver(ObjectMapper objectMapper) {
        return new JsonPropertyPathResolver(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    JsonFieldNameSortRewriter dalJsonFieldNameSortRewriter(JsonPropertyPathResolver pathResolver) {
        return new JsonFieldNameSortRewriter(pathResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    DalValidator dalValidator(
        SpringValidatorAdapter validatorAdapter,
        JsonPropertyPathResolver pathResolver
    ) {
        return new DefaultDalValidator(validatorAdapter, pathResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    DefaultSimpleTypePredicate simpleTypePredicate(ObjectProvider<SimpleTypeContributor> contributors) {
        return new DefaultSimpleTypePredicate(contributors.stream()
            .flatMap(contributor -> contributor.simpleTypes().stream())
            .collect(Collectors.toSet()));
    }

    @Bean
    @ConditionalOnMissingBean
    DalTransactionPolicy dalTransactionPolicy() {
        return new DefaultDalTransactionPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    TelaioVersionProvider telaioVersionProvider() {
        return new TelaioVersionProvider();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    DalManager inMemoryDalManager(ListableBeanFactory beanFactory) {
        return new InMemoryDalManager(beanFactory);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    DalDefinitionBeanPostProcessor dalDefinitionBeanPostProcessor(DalManager dalManager) {
        return new DalDefinitionBeanPostProcessor(dalManager);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    DalFactoryPostProcessor dalFactoryPostProcessor() {
        return new DalFactoryPostProcessor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    DalInterceptionBeanPostProcessor dalInterceptionBeanPostProcessor(
        ObjectProvider<DalInterceptorProvider> interceptorProviders
    ) {
        return new DalInterceptionBeanPostProcessor(interceptorProviders);
    }

    @Bean
    @ConditionalOnMissingBean
    DalOperationAdapterRegistry dalOperationAdapterRegistry() {
        return new InMemoryDalOperationAdapterRegistry();
    }

    /**
     * Assembles one interceptor-wrapped adapter chain per (channel, DAL) after all singletons exist and
     * publishes each to the {@link DalOperationAdapterRegistry}.
     *
     * <p>Every {@link DalChannelDefinition} bean contributed by a channel module defines one channel and
     * the operations each DAL exposes on it; internal DALs and DALs exposing nothing on a channel get no
     * chain there. With no channel module present, no chain is built. Providers from every module (audit,
     * security, any channel module) are collected regardless of autoconfiguration order.</p>
     */
    @Bean
    DalOperationAdapterAssembler dalOperationAdapterAssembler(
        DalManager dalManager,
        DalOperationAdapterRegistry registry,
        ObjectProvider<DalChannelDefinition> channelDefinitions,
        List<DalAdapterInterceptorProvider> interceptorProviders
    ) {
        return new DalOperationAdapterAssembler(
            dalManager, registry, channelDefinitions.orderedStream().toList(), interceptorProviders);
    }
}
