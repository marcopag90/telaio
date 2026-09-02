package com.paganbit.telaio.metrics.autoconfigure;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

import javax.sql.DataSource;

/**
 * Matches when at least one {@link DataSource} bean definition exists, <em>including</em> beans declared
 * with {@code defaultCandidate = false}.
 *
 * <p>Spring Boot's {@code @ConditionalOnBean} ignores non-default candidates, so a metrics DataSource
 * declared with the recommended {@code @Bean(defaultCandidate = false) @TelaioMetricsDataSource} recipe
 * would silently leave the JDBC store unregistered whenever it is the application's only DataSource.
 * This condition looks at the raw bean definitions instead. Like {@code @ConditionalOnBean} it is
 * evaluated in the {@link ConfigurationPhase#REGISTER_BEAN} phase, after the autoconfigurations this
 * one is ordered behind have contributed their definitions.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public class OnMetricsDataSourceCondition implements ConfigurationCondition {

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        final var beanFactory = context.getBeanFactory();
        if (beanFactory == null) {
            return false;
        }
        return beanFactory.getBeanNamesForType(DataSource.class, true, false).length > 0;
    }
}
