package com.paganbit.telaio.web.interceptor;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalAdapterContext;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationAdapterAssembler;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.adapter.InMemoryDalOperationAdapterRegistry;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.web.exception.DalOperationNotExposedException;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Proves the REST exposure provider plugs into core's {@link DalOperationAdapterAssembler} like any other
 * {@code DalAdapterInterceptorProvider}: a partially exposed DAL is assembled with the interceptor, and the
 * provider contributes no interceptor for a fully exposed DAL.
 */
@ExtendWith(MockitoExtension.class)
class WebDalExposureInterceptorProviderTest {

    @Mock
    private DalManager dalManager;

    private final InMemoryDalOperationAdapterRegistry registry = new InMemoryDalOperationAdapterRegistry();

    @Test
    void shouldRejectNonExposedOperationThroughTheAssembledChain() {
        DalDefinitionEntry partial = new DalDefinitionEntry(
            "partialDal", TestDalService.class, false,
            EnumSet.of(DalOperationType.CREATE, DalOperationType.READ));
        when(dalManager.getAllDefinitions()).thenReturn(List.of(partial));
        doReturn(new TestDalService()).when(dalManager).getServiceByName("partialDal");
        when(dalManager.getDefinitionByName("partialDal")).thenReturn(partial);

        DalOperationAdapterAssembler assembler = new DalOperationAdapterAssembler(
            dalManager, registry, List.of(new WebDalExposureInterceptorProvider()));
        assembler.afterSingletonsInstantiated();

        DalOperationAdapter<Object, Object> adapter = registry.get("partialDal");
        // CREATE is exposed and passes through to the DAL.
        assertEquals(TestDalService.CREATED, adapter.create(Map.of()));
        // DELETE is not exposed and is rejected before reaching the DAL.
        assertThrows(DalOperationNotExposedException.class, () -> adapter.delete(1L));
    }

    @Test
    void shouldContributeNoInterceptorWhenEveryOperationIsExposed() {
        DalDefinitionEntry full = new DalDefinitionEntry("fullDal", TestDalService.class);
        when(dalManager.getDefinitionByName("fullDal")).thenReturn(full);
        DalAdapterContext context = new DalAdapterContext("fullDal", TestDalService.class, dalManager);

        assertNull(new WebDalExposureInterceptorProvider().getInterceptor(context));
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
