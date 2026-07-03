package io.paganbit.telaio.core.beans.registration;

import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.core.interceptor.DalInterceptionContext;
import io.paganbit.telaio.core.interceptor.DalInterceptorProvider;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DalInterceptionBeanPostProcessorTest {

    @Mock
    private ObjectProvider<DalInterceptorProvider> interceptorProviders;

    private DalInterceptionBeanPostProcessor postProcessor;
    private List<String> trace;

    @BeforeEach
    void setUp() {
        postProcessor = new DalInterceptionBeanPostProcessor(interceptorProviders);
        trace = new ArrayList<>();
    }

    @Test
    void nonDalBean_shouldBeReturnedUnchanged() {
        Object bean = new Object();

        assertThat(postProcessor.postProcessAfterInitialization(bean, "plainBean")).isSameAs(bean);
    }

    @Test
    void dalWithoutDalService_shouldBeReturnedUnchanged() {
        StubDalWithoutService bean = new StubDalWithoutService(trace);

        assertThat(postProcessor.postProcessAfterInitialization(bean, "unnamedDal")).isSameAs(bean);
    }

    @Test
    void whenNoProviderContributes_shouldReturnSameInstance() {
        when(interceptorProviders.stream()).thenAnswer(inv -> Stream.of(provider(null, 0)));
        StubDal bean = new StubDal(trace);

        assertThat(postProcessor.postProcessAfterInitialization(bean, "stubDal")).isSameAs(bean);
    }

    @Test
    void whenProviderContributes_shouldProxyPreservingTargetClass() {
        when(interceptorProviders.stream())
            .thenAnswer(inv -> Stream.of(provider(tracing("audit"), 0)));

        Object processed = postProcessor.postProcessAfterInitialization(new StubDal(trace), "stubDal");

        assertThat(AopUtils.isCglibProxy(processed)).isTrue();
        assertThat(processed).isInstanceOf(StubDal.class);
        ((StubDal) processed).create(Map.of("a", 1));
        assertThat(trace).containsExactly("audit", "target");
    }

    @Test
    void multipleProviders_shouldApplyInterceptorsInOrder() {
        // Registered out of order on purpose — the lower order value must end up outermost
        when(interceptorProviders.stream()).thenAnswer(inv -> Stream.of(
            provider(tracing("inner"), 0),
            provider(tracing("outer"), -100)));

        Object processed = postProcessor.postProcessAfterInitialization(new StubDal(trace), "stubDal");

        ((StubDal) processed).create(Map.of("a", 1));
        assertThat(trace).containsExactly("outer", "inner", "target");
    }

    @Test
    void provider_shouldReceiveDalNameAndUserClass() {
        List<DalInterceptionContext> contexts = new ArrayList<>();
        DalInterceptorProvider capturing = new DalInterceptorProvider() {
            @Override
            public @Nullable MethodInterceptor getInterceptor(DalInterceptionContext context) {
                contexts.add(context);
                return null;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };
        when(interceptorProviders.stream()).thenAnswer(inv -> Stream.of(capturing));

        postProcessor.postProcessAfterInitialization(new StubDal(trace), "stubDal");

        assertThat(contexts).hasSize(1);
        assertThat(contexts.getFirst().dalName()).isEqualTo("stubs");
        assertThat(contexts.getFirst().dalBeanClass()).isEqualTo(StubDal.class);
    }

    private static DalInterceptorProvider provider(@Nullable MethodInterceptor interceptor, int order) {
        return new DalInterceptorProvider() {
            @Override
            public @Nullable MethodInterceptor getInterceptor(DalInterceptionContext context) {
                return interceptor;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    private MethodInterceptor tracing(String label) {
        return invocation -> {
            trace.add(label);
            return invocation.proceed();
        };
    }

    /**
     * Minimal {@link Dal} that records each {@code create} invocation on the shared trace.
     */
    static class TraceableDal implements Dal<Object, Long> {

        private final List<String> trace;

        TraceableDal(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public Object create(Map<String, Object> properties) {
            trace.add("target");
            return properties;
        }

        @Override
        public Page<Object> read(@Nullable FilterNode filter, Pageable pageable) {
            return new PageImpl<>(List.of());
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

    @DalService(name = "stubs")
    static class StubDal extends TraceableDal {

        StubDal(List<String> trace) {
            super(trace);
        }
    }

    static class StubDalWithoutService extends TraceableDal {

        StubDalWithoutService(List<String> trace) {
            super(trace);
        }
    }
}
