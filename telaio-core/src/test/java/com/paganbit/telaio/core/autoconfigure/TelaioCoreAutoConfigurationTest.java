package com.paganbit.telaio.core.autoconfigure;

import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.beans.DefaultDalPropertyMerger;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.core.transaction.DefaultDalTransactionPolicy;
import com.paganbit.telaio.core.version.TelaioVersionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class TelaioCoreAutoConfigurationTest {

    private final ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TelaioCoreAutoConfiguration.class))
        .withUserConfiguration(BasicConfig.class);

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
