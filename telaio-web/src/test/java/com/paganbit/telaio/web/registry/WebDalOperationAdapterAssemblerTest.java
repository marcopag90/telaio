package com.paganbit.telaio.web.registry;

import com.turkraft.springfilter.parser.node.FilterNode;
import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalAdapterContext;
import com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.web.exception.DalOperationNotExposedException;
import com.paganbit.telaio.web.interceptor.WebDalExposureInterceptorProvider;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebDalOperationAdapterAssemblerTest {

    @Mock
    private DalManager dalManager;

    private final DefaultWebDalOperationAdapterRegistry registry = new DefaultWebDalOperationAdapterRegistry();

    @BeforeEach
    void stubManager() {
        lenient().when(dalManager.getAllDefinitions())
            .thenReturn(List.of(new DalDefinitionEntry("testDal", TestDalService.class)));
        lenient().doReturn(new TestDalService()).when(dalManager).getServiceByName("testDal");
    }

    @Test
    void shouldAssembleBaseAdapterDelegatingToDalService() {
        WebDalOperationAdapterAssembler assembler = new WebDalOperationAdapterAssembler(dalManager, registry, List.of());

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
            public MethodInterceptor getInterceptor(
                DalAdapterContext context) {
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

        final var assembler = new WebDalOperationAdapterAssembler(dalManager, registry, List.of(provider));
        assembler.afterSingletonsInstantiated();

        DalOperationAdapter<Object, Object> adapter = registry.get("testDal");
        adapter.delete(1L);

        assertTrue(intercepted.get(), "Provider interceptor should be woven around the adapter");
    }

    @Test
    void shouldNotExposeInternalDal() {
        when(dalManager.getAllDefinitions())
            .thenReturn(List.of(new DalDefinitionEntry("internalDal", TestDalService.class, true)));

        WebDalOperationAdapterAssembler assembler = new WebDalOperationAdapterAssembler(dalManager, registry, List.of());
        assembler.afterSingletonsInstantiated();

        // No adapter is registered for an internal DAL, so resolving it fails as if it did not exist.
        assertThrows(DalNotFoundException.class, () -> registry.get("internalDal"));
    }

    @Test
    void shouldRejectNonExposedOperationThroughExposureProvider() {
        DalDefinitionEntry partial = new DalDefinitionEntry(
            "partialDal", TestDalService.class, false,
            EnumSet.of(DalOperationType.CREATE, DalOperationType.READ));
        when(dalManager.getAllDefinitions()).thenReturn(List.of(partial));
        doReturn(new TestDalService()).when(dalManager).getServiceByName("partialDal");
        when(dalManager.getDefinitionByName("partialDal")).thenReturn(partial);

        WebDalOperationAdapterAssembler assembler = new WebDalOperationAdapterAssembler(
            dalManager, registry, List.of(new WebDalExposureInterceptorProvider()));
        assembler.afterSingletonsInstantiated();

        DalOperationAdapter<Object, Object> adapter = registry.get("partialDal");
        // CREATE is exposed and passes through to the DAL.
        assertEquals(TestDalService.CREATED, adapter.create(Map.of()));
        // DELETE is not exposed and is rejected before reaching the DAL.
        assertThrows(DalOperationNotExposedException.class, () -> adapter.delete(1L));
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
