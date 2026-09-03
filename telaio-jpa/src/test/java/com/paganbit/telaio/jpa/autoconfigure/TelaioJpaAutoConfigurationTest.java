package com.paganbit.telaio.jpa.autoconfigure;

import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.jpa.filter.JsonAwareFilterSpecificationConverter;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.converter.FilterSpecificationConverterImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link ApplicationContextRunner} tests for {@link TelaioJpaAutoConfiguration}: the JSON-aware
 * decorator is registered as the primary converter next to Turkraft's, backs off when Turkraft's
 * converter is absent or when the application declares its own, and the whole autoconfiguration
 * backs off when the Turkraft jpa artifact is not on the classpath.
 */
class TelaioJpaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TelaioJpaAutoConfiguration.class));

    private final FilterSpecificationConverterImpl turkraftConverter = mock(FilterSpecificationConverterImpl.class);

    private ApplicationContextRunner withTurkraftConverter() {
        return runner
            .withBean(FilterSpecificationConverterImpl.class, () -> turkraftConverter)
            .withBean(JsonPropertyPathResolver.class,
                () -> new JsonPropertyPathResolver(JsonMapper.builder().build()));
    }

    @Test
    void converter_decoratesTurkraftConverterAsPrimary() {
        withTurkraftConverter()
            .withUserConfiguration(ConverterByTypeConsumer.class)
            .run(context -> {
                // Two FilterSpecificationConverter beans coexist: Turkraft's and the primary decorator.
                assertThat(context.getBeansOfType(FilterSpecificationConverter.class)).hasSize(2);
                FilterSpecificationConverter primary = context.getBean(FilterSpecificationConverter.class);
                assertThat(primary).isInstanceOf(JsonAwareFilterSpecificationConverter.class);
                assertThat(ReflectionTestUtils.getField(primary, "delegate")).isSameAs(turkraftConverter);
                assertThat(context.getBean(ConverterByTypeConsumer.class).converter).isSameAs(primary);
            });
    }

    @Test
    void converter_backsOffWithoutTurkraftConverter() {
        runner.run(context -> assertThat(context).doesNotHaveBean(FilterSpecificationConverter.class));
    }

    /**
     * A user-declared decorator replaces the autoconfigured one. Marked primary, as the contract requires:
     * Turkraft's own converter stays in the context, so a by-type injection point (what {@code JpaDal}
     * declares) must still resolve unambiguously.
     */
    @Test
    void converter_backsOffWhenUserDefinesTheDecorator() {
        JsonAwareFilterSpecificationConverter custom = new JsonAwareFilterSpecificationConverter(
            turkraftConverter, new JsonPropertyPathResolver(JsonMapper.builder().build()));
        withTurkraftConverter()
            .withBean("customConverter", JsonAwareFilterSpecificationConverter.class, () -> custom,
                definition -> definition.setPrimary(true))
            .withUserConfiguration(ConverterByTypeConsumer.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(JsonAwareFilterSpecificationConverter.class);
                assertThat(context.getBean(JsonAwareFilterSpecificationConverter.class)).isSameAs(custom);
                assertThat(context.getBean(ConverterByTypeConsumer.class).converter).isSameAs(custom);
            });
    }

    /**
     * Any user-declared converter (not only the decorator type) makes the autoconfigured one back off;
     * Turkraft's own implementation is ignored by the guard, so the user bean must be primary.
     */
    @Test
    void converter_backsOffWhenUserDefinesAnyOtherConverter() {
        FilterSpecificationConverter custom = mock(FilterSpecificationConverter.class);
        withTurkraftConverter()
            .withBean("customConverter", FilterSpecificationConverter.class, () -> custom,
                definition -> definition.setPrimary(true))
            .withUserConfiguration(ConverterByTypeConsumer.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(JsonAwareFilterSpecificationConverter.class);
                assertThat(context.getBean(FilterSpecificationConverter.class)).isSameAs(custom);
                assertThat(context.getBean(ConverterByTypeConsumer.class).converter).isSameAs(custom);
            });
    }

    @Test
    void autoConfiguration_backsOffWithoutTurkraftJpaOnClasspath() {
        runner
            .withClassLoader(new FilteredClassLoader(FilterSpecificationConverter.class))
            .run(context -> assertThat(context).doesNotHaveBean(TelaioJpaAutoConfiguration.class));
    }

    static class ConverterByTypeConsumer {

        FilterSpecificationConverter converter;

        @Autowired
        public void setConverter(FilterSpecificationConverter converter) {
            this.converter = converter;
        }
    }
}
