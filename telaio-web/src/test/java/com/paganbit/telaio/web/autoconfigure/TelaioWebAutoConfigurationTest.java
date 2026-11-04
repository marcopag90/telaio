package com.paganbit.telaio.web.autoconfigure;

import com.paganbit.telaio.core.adapter.DalOperationAdapterRegistry;
import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.web.annotation.DalIdArgumentResolver;
import com.paganbit.telaio.web.annotation.DalWeb;
import com.paganbit.telaio.web.channel.DalChannels;
import com.paganbit.telaio.web.channel.DalRestChannelDefinition;
import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import com.paganbit.telaio.core.exception.DalOperationNotExposedException;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Verifies the bean registration and conditional wiring of {@link TelaioWebAutoConfiguration}.
 */
class TelaioWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            TelaioCoreAutoConfiguration.class,
            TelaioWebAutoConfiguration.class))
        .withBean("sfConversionService", ConversionService.class, DefaultConversionService::new)
        .withBean(SpringValidatorAdapter.class,
            () -> new SpringValidatorAdapter(mock(jakarta.validation.Validator.class)))
        .withBean(FilterStringConverter.class, () -> mock(FilterStringConverter.class));

    @Test
    void byDefault_shouldRegisterWebBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DalIdArgumentResolver.class);
            assertThat(context).hasSingleBean(DalRestChannelDefinition.class);
            assertThat(context).hasSingleBean(DalOperationAdapterRegistry.class);
            assertThat(context).doesNotHaveBean(DalAdapterInterceptorProvider.class);
        });
    }

    @Test
    void reactiveWebContext_shouldNotLoadTelaioWebAutoConfiguration() {
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TelaioWebAutoConfiguration.class))
            .run(context ->
                assertThat(context).doesNotHaveBean(DalIdArgumentResolver.class));
    }

    @Test
    void restChannel_shouldEnforceDalWebExposurePerDal() {
        contextRunner
            .withBean("openDal", OpenDal.class, OpenDal::new)
            .withBean("readOnlyDal", ReadOnlyDal.class, ReadOnlyDal::new)
            .withBean("hiddenDal", HiddenDal.class, HiddenDal::new)
            .run(context -> {
                DalOperationAdapterRegistry registry = context.getBean(DalOperationAdapterRegistry.class);

                DalOperationAdapter<Object> open = registry.get(DalChannels.REST, "openDal");
                assertThat(open.create(Map.of())).isSameAs(StubDal.CREATED);

                DalOperationAdapter<Object> readOnly = registry.get(DalChannels.REST, "readOnlyDal");
                assertThat(readOnly.readOne(1L)).isNotNull();
                assertThatThrownBy(() -> readOnly.delete(1L))
                    .isInstanceOfSatisfying(DalOperationNotExposedException.class, ex -> {
                        assertThat(ex.channel()).isEqualTo(DalChannels.REST);
                        assertThat(ex.operation()).isEqualTo(DalOperationType.DELETE);
                    });

                assertThatThrownBy(() -> registry.get(DalChannels.REST, "hiddenDal"))
                    .isInstanceOf(DalNotFoundException.class);
            });
    }

    @DalService(name = "openDal")
    static class OpenDal extends StubDal {
    }

    @DalService(name = "readOnlyDal")
    @DalWeb(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
    static class ReadOnlyDal extends StubDal {
    }

    @DalService(name = "hiddenDal")
    @DalWeb(operations = {})
    static class HiddenDal extends StubDal {
    }

    abstract static class StubDal implements Dal<Object, Long> {

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
            return Optional.of(CREATED);
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
}
