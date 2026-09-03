package com.paganbit.telaio.metrics.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the decision matrix of {@link OnMetricsDataSourceCondition}: any {@link DataSource} bean
 * definition activates the JDBC store — including one declared with {@code defaultCandidate = false},
 * which {@code @ConditionalOnBean} would ignore — while a context without DataSources, or a
 * condition evaluated without a bean factory, does not.
 */
class OnMetricsDataSourceConditionTest {

    private final OnMetricsDataSourceCondition condition = new OnMetricsDataSourceCondition();

    @Test
    void isEvaluatedInTheRegisterBeanPhase() {
        assertThat(condition.getConfigurationPhase()).isEqualTo(ConfigurationPhase.REGISTER_BEAN);
    }

    @Test
    void withDefaultCandidateDataSource_shouldMatch() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("dataSource", new RootBeanDefinition(SimpleDriverDataSource.class));

        assertThat(condition.matches(context(beanFactory), mock(AnnotatedTypeMetadata.class))).isTrue();
    }

    @Test
    void withNonDefaultCandidateDataSource_shouldStillMatch() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition marked = new RootBeanDefinition(SimpleDriverDataSource.class);
        marked.setDefaultCandidate(false);
        beanFactory.registerBeanDefinition("metricsDataSource", marked);

        assertThat(condition.matches(context(beanFactory), mock(AnnotatedTypeMetadata.class))).isTrue();
    }

    @Test
    void withoutDataSource_shouldNotMatch() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("other", new RootBeanDefinition(Object.class));

        assertThat(condition.matches(context(beanFactory), mock(AnnotatedTypeMetadata.class))).isFalse();
    }

    @Test
    void withoutBeanFactory_shouldNotMatch() {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getBeanFactory()).thenReturn(null);

        assertThat(condition.matches(context, mock(AnnotatedTypeMetadata.class))).isFalse();
    }

    private static ConditionContext context(DefaultListableBeanFactory beanFactory) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getBeanFactory()).thenReturn(beanFactory);
        return context;
    }
}
