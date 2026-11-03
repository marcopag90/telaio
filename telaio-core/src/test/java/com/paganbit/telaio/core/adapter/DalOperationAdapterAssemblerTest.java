package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.core.registry.DalManager;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalOperationAdapterAssemblerTest {

    @Mock
    private DalManager dalManager;

    private final InMemoryDalOperationAdapterRegistry registry = new InMemoryDalOperationAdapterRegistry();

    @BeforeEach
    void stubManager() {
        lenient().when(dalManager.getAllDefinitions())
            .thenReturn(List.of(new DalDefinitionEntry("testDal", TestDalService.class)));
        lenient().doReturn(new TestDalService()).when(dalManager).getServiceByName("testDal");
    }

    @Test
    void shouldAssembleBaseAdapterDelegatingToDalService() {
        DalOperationAdapterAssembler assembler = new DalOperationAdapterAssembler(dalManager, registry, List.of());

        assembler.afterSingletonsInstantiated();

        DalOperationAdapter<Object, Object> adapter = registry.get("testDal");
        assertNotNull(adapter);
        assertEquals(TestDalService.CREATED, adapter.create(Map.of("k", "v")));
    }

    @Test
    void shouldWeaveInterceptorsFromProviders() {
        AtomicBoolean intercepted = new AtomicBoolean(false);
        final var provider = new DalAdapterInterceptorProvider() {
            @Override
            public MethodInterceptor getInterceptor(DalAdapterContext context) {
                return invocation -> {
                    intercepted.set(true);
                    return invocation.proceed();
                };
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };

        final var assembler = new DalOperationAdapterAssembler(dalManager, registry, List.of(provider));
        assembler.afterSingletonsInstantiated();

        DalOperationAdapter<Object, Object> adapter = registry.get("testDal");
        adapter.delete(1L);

        assertTrue(intercepted.get(), "Provider interceptor should be woven around the adapter");
    }

    @Test
    void shouldApplyProvidersInOrder_lowestOrderOutermost() {
        List<String> trace = new ArrayList<>();
        // Declared inner-first, to prove the assembler sorts by order rather than by declaration.
        final var inner = tracingProvider("inner", DalAdapterInterceptorProvider.SECURITY_PRECEDENCE, trace);
        final var outer = tracingProvider("outer", DalAdapterInterceptorProvider.EXPOSURE_PRECEDENCE, trace);

        final var assembler = new DalOperationAdapterAssembler(dalManager, registry, List.of(inner, outer));
        assembler.afterSingletonsInstantiated();

        registry.get("testDal").delete(1L);

        assertEquals(List.of("outer", "inner"), trace);
    }

    @Test
    void shouldSkipProvidersThatReturnNoInterceptor() {
        final var absent = new DalAdapterInterceptorProvider() {
            @Override
            public @Nullable MethodInterceptor getInterceptor(DalAdapterContext context) {
                return null;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };

        final var assembler = new DalOperationAdapterAssembler(dalManager, registry, List.of(absent));
        assembler.afterSingletonsInstantiated();

        assertEquals(TestDalService.CREATED, registry.get("testDal").create(Map.of()));
    }

    @Test
    void shouldHandProvidersTheContextOfTheDalBeingAssembled() {
        AtomicReference<@Nullable DalAdapterContext> seen = new AtomicReference<>();
        final var provider = new DalAdapterInterceptorProvider() {
            @Override
            public @Nullable MethodInterceptor getInterceptor(DalAdapterContext context) {
                seen.set(context);
                return null;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };

        new DalOperationAdapterAssembler(dalManager, registry, List.of(provider)).afterSingletonsInstantiated();

        DalAdapterContext context = seen.get();
        assertNotNull(context);
        assertEquals("testDal", context.dalName());
        assertEquals(TestDalService.class, context.dalBeanClass());
        assertSame(dalManager, context.dalManager());
    }

    @Test
    void shouldRegisterEveryNonInternalDalAndSkipTheInternalOnes() {
        when(dalManager.getAllDefinitions()).thenReturn(List.of(
            new DalDefinitionEntry("first", TestDalService.class),
            new DalDefinitionEntry("hidden", TestDalService.class, true),
            new DalDefinitionEntry("second", TestDalService.class)));
        doReturn(new TestDalService()).when(dalManager).getServiceByName("first");
        doReturn(new TestDalService()).when(dalManager).getServiceByName("second");

        new DalOperationAdapterAssembler(dalManager, registry, List.of()).afterSingletonsInstantiated();

        assertNotNull(registry.get("first"));
        assertNotNull(registry.get("second"));
        assertThrows(DalNotFoundException.class, () -> registry.get("hidden"));
        verify(dalManager, never()).getServiceByName("hidden");
    }

    @Test
    void shouldNotExposeInternalDal() {
        when(dalManager.getAllDefinitions())
            .thenReturn(List.of(new DalDefinitionEntry("internalDal", TestDalService.class, true)));

        DalOperationAdapterAssembler assembler = new DalOperationAdapterAssembler(dalManager, registry, List.of());
        assembler.afterSingletonsInstantiated();

        // No adapter is registered for an internal DAL, so resolving it fails as if it did not exist.
        assertThrows(DalNotFoundException.class, () -> registry.get("internalDal"));
    }

    private static DalAdapterInterceptorProvider tracingProvider(String label, int order, List<String> trace) {
        return new DalAdapterInterceptorProvider() {
            @Override
            public MethodInterceptor getInterceptor(DalAdapterContext context) {
                return invocation -> {
                    trace.add(label);
                    return invocation.proceed();
                };
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    static class TestDalService implements Dal<Object, Long> {

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
}
