package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.channel.DalChannelDefinition;
import com.paganbit.telaio.core.exception.DalDefinitionException;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import com.paganbit.telaio.core.exception.DalOperationNotExposedException;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalOperationAdapterAssemblerTest {

    private static final DalChannel REST = DalChannel.of("rest");
    private static final DalChannel AGENT = DalChannel.of("agent");

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
        assembler(List.of(exposingAll(REST)), List.of()).afterSingletonsInstantiated();

        DalOperationAdapter<Object> adapter = registry.get(REST, "testDal");
        assertNotNull(adapter);
        assertEquals(TestDalService.CREATED, adapter.create(Map.of("k", "v")));
    }

    @Test
    void withoutChannelDefinitions_shouldAssembleNothing() {
        assembler(List.of(), List.of()).afterSingletonsInstantiated();

        assertThrows(DalNotFoundException.class, () -> registry.get(REST, "testDal"));
        verify(dalManager, never()).getServiceByName(any());
    }

    @Test
    void shouldAssembleOneIndependentChainPerChannel() {
        DalChannelDefinition rest = exposingAll(REST);
        DalChannelDefinition agent = exposing(AGENT, EnumSet.of(DalOperationType.READ, DalOperationType.READ_ONE));

        assembler(List.of(rest, agent), List.of()).afterSingletonsInstantiated();

        DalOperationAdapter<Object> restAdapter = registry.get(REST, "testDal");
        DalOperationAdapter<Object> agentAdapter = registry.get(AGENT, "testDal");
        assertNotSame(restAdapter, agentAdapter);
        // The same DAL, two exposure rules: CREATE passes on rest and is rejected on agent.
        assertEquals(TestDalService.CREATED, restAdapter.create(Map.of()));
        DalOperationNotExposedException rejected =
            assertThrows(DalOperationNotExposedException.class, () -> agentAdapter.create(Map.of()));
        assertEquals(AGENT, rejected.channel());
        assertEquals("testDal", rejected.dalName());
        assertEquals(DalOperationType.CREATE, rejected.operation());
    }

    @Test
    void dalExposingNothingOnAChannel_shouldHaveNoChainThere() {
        DalChannelDefinition rest = exposingAll(REST);
        DalChannelDefinition agent = exposing(AGENT, EnumSet.noneOf(DalOperationType.class));

        assembler(List.of(rest, agent), List.of()).afterSingletonsInstantiated();

        assertNotNull(registry.get(REST, "testDal"));
        assertThrows(DalNotFoundException.class, () -> registry.get(AGENT, "testDal"));
    }

    @Test
    void duplicateChannelDefinition_shouldFailFast() {
        DalOperationAdapterAssembler assembler =
            assembler(List.of(exposingAll(REST), exposingAll(DalChannel.of("rest"))), List.of());

        DalDefinitionException ex = assertThrows(DalDefinitionException.class, assembler::afterSingletonsInstantiated);
        assertTrue(ex.getMessage().contains("rest"));
    }

    @Test
    void shouldHandEachProviderTheContextOfEveryChannel() {
        List<DalAdapterContext> seen = new ArrayList<>();
        DalAdapterInterceptorProvider provider = new DalAdapterInterceptorProvider() {
            @Override
            public @Nullable MethodInterceptor getInterceptor(DalAdapterContext context) {
                seen.add(context);
                return null;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };

        assembler(List.of(exposingAll(REST), exposingAll(AGENT)), List.of(provider)).afterSingletonsInstantiated();

        assertEquals(2, seen.size());
        assertEquals(List.of(REST, AGENT), seen.stream().map(DalAdapterContext::channel).toList());
        for (DalAdapterContext context : seen) {
            assertEquals("testDal", context.dalName());
            assertEquals(TestDalService.class, context.dalBeanClass());
            assertSame(dalManager, context.catalog());
        }
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

        assembler(List.of(exposingAll(REST)), List.of(provider)).afterSingletonsInstantiated();

        registry.get(REST, "testDal").delete(1L);

        assertTrue(intercepted.get(), "Provider interceptor should be woven around the adapter");
    }

    @Test
    void shouldApplyProvidersInOrder_lowestOrderOutermost() {
        List<String> trace = new ArrayList<>();
        // Declared inner-first, to prove the assembler sorts by order rather than by declaration.
        final var inner = tracingProvider("inner", DalAdapterInterceptorProvider.SECURITY_PRECEDENCE, trace);
        final var outer = tracingProvider("outer", DalAdapterInterceptorProvider.AUDIT_PRECEDENCE, trace);

        assembler(List.of(exposingAll(REST)), List.of(inner, outer)).afterSingletonsInstantiated();

        registry.get(REST, "testDal").delete(1L);

        assertEquals(List.of("outer", "inner"), trace);
    }

    @Test
    void exposureInterceptor_shouldBeOutsideEveryProvider() {
        List<String> trace = new ArrayList<>();
        // A provider claiming the highest precedence still runs inside core's exposure check.
        final var provider = tracingProvider("provider", DalAdapterInterceptorProvider.EXPOSURE_PRECEDENCE, trace);
        DalChannelDefinition readOnly = exposing(REST, EnumSet.of(DalOperationType.READ));

        assembler(List.of(readOnly), List.of(provider)).afterSingletonsInstantiated();
        DalOperationAdapter<Object> adapter = registry.get(REST, "testDal");

        assertThrows(DalOperationNotExposedException.class, () -> adapter.delete(1L));
        assertTrue(trace.isEmpty(), "a rejected call must not reach any provider interceptor");
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

        assembler(List.of(exposingAll(REST)), List.of(absent)).afterSingletonsInstantiated();

        assertEquals(TestDalService.CREATED, registry.get(REST, "testDal").create(Map.of()));
    }

    @Test
    void shouldRegisterEveryNonInternalDalAndSkipTheInternalOnes() {
        when(dalManager.getAllDefinitions()).thenReturn(List.of(
            new DalDefinitionEntry("first", TestDalService.class),
            new DalDefinitionEntry("hidden", TestDalService.class, true),
            new DalDefinitionEntry("second", TestDalService.class)));
        doReturn(new TestDalService()).when(dalManager).getServiceByName("first");
        doReturn(new TestDalService()).when(dalManager).getServiceByName("second");
        DalChannelDefinition rest = spy(exposingAll(REST));

        assembler(List.of(rest), List.of()).afterSingletonsInstantiated();

        assertNotNull(registry.get(REST, "first"));
        assertNotNull(registry.get(REST, "second"));
        assertThrows(DalNotFoundException.class, () -> registry.get(REST, "hidden"));
        verify(dalManager, never()).getServiceByName("hidden");
        // An internal DAL is never even asked about: internal wins over any channel declaration.
        verify(rest, times(2)).exposedOperations(TestDalService.class);
    }

    private DalOperationAdapterAssembler assembler(
        List<DalChannelDefinition> definitions,
        List<DalAdapterInterceptorProvider> providers
    ) {
        return new DalOperationAdapterAssembler(dalManager, registry, definitions, providers);
    }

    private static DalChannelDefinition exposingAll(DalChannel channel) {
        return exposing(channel, EnumSet.allOf(DalOperationType.class));
    }

    private static DalChannelDefinition exposing(DalChannel channel, Set<DalOperationType> operations) {
        return new DalChannelDefinition() {
            @Override
            public DalChannel channel() {
                return channel;
            }

            @Override
            public Set<DalOperationType> exposedOperations(Class<?> dalClass) {
                return operations;
            }
        };
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
