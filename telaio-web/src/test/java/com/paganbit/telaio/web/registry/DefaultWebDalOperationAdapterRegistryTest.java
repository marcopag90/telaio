package com.paganbit.telaio.web.registry;

import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DefaultWebDalOperationAdapterRegistryTest {

    private DefaultWebDalOperationAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultWebDalOperationAdapterRegistry();
    }

    @Test
    void register_andGet_shouldReturnStoredAdapter() {
        DalOperationAdapter<?, ?> adapter = mock(DalOperationAdapter.class);

        registry.register("myDal", adapter);

        assertSame(adapter, registry.get("myDal"));
    }

    @Test
    void get_withUnknownName_shouldThrowDalNotFoundException() {
        DalNotFoundException ex = assertThrows(
            DalNotFoundException.class,
            () -> registry.get("unknown")
        );
        assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    void register_whenCalledTwice_firstRegistrationWins() {
        DalOperationAdapter<?, ?> first = mock(DalOperationAdapter.class);
        DalOperationAdapter<?, ?> second = mock(DalOperationAdapter.class);

        registry.register("myDal", first);
        registry.register("myDal", second);

        assertSame(first, registry.get("myDal"), "First registration should win");
    }

    @Test
    void register_multipleAdapters_eachRetrievableByName() {
        DalOperationAdapter<?, ?> adapterA = mock(DalOperationAdapter.class);
        DalOperationAdapter<?, ?> adapterB = mock(DalOperationAdapter.class);

        registry.register("dalA", adapterA);
        registry.register("dalB", adapterB);

        assertSame(adapterA, registry.get("dalA"));
        assertSame(adapterB, registry.get("dalB"));
    }
}
