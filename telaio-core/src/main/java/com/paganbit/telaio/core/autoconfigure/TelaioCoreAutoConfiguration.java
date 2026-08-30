package com.paganbit.telaio.core.autoconfigure;

import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.beans.DefaultDalPropertyMerger;
import com.paganbit.telaio.core.beans.registration.DalDefinitionBeanPostProcessor;
import com.paganbit.telaio.core.beans.registration.DalFactoryPostProcessor;
import com.paganbit.telaio.core.beans.registration.DalInterceptionBeanPostProcessor;
import com.paganbit.telaio.core.interceptor.DalInterceptorProvider;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.core.registry.InMemoryDalManager;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.core.transaction.DefaultDalTransactionPolicy;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
    DalPropertyMerger dalPropertyMerger(ObjectProvider<ObjectMapper> objectMapper) {
        return new DefaultDalPropertyMerger(objectMapper.getIfAvailable(() -> JsonMapper.builder().build()));
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
}
