package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class InMemoryDalOperationAdapterRegistryTest {

    private static final DalChannel REST = DalChannel.of("rest");
    private static final DalChannel AGENT = DalChannel.of("agent");

    private InMemoryDalOperationAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryDalOperationAdapterRegistry();
    }

    @Test
    void register_andGet_shouldReturnStoredAdapter() {
        DalOperationAdapter<?> adapter = mock(DalOperationAdapter.class);

        registry.register(REST, "myDal", adapter);

        assertSame(adapter, registry.get(REST, "myDal"));
    }

    @Test
    void get_withUnknownName_shouldThrowDalNotFoundException() {
        DalNotFoundException ex = assertThrows(
            DalNotFoundException.class,
            () -> registry.get(REST, "unknown")
        );
        assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    void get_onAChannelTheDalIsNotRegisteredOn_shouldThrowDalNotFoundException() {
        DalOperationAdapter<?> adapter = mock(DalOperationAdapter.class);
        registry.register(REST, "myDal", adapter);

        assertThrows(DalNotFoundException.class, () -> registry.get(AGENT, "myDal"));
    }

    @Test
    void sameName_onTwoChannels_shouldHoldTwoIndependentAdapters() {
        DalOperationAdapter<?> restAdapter = mock(DalOperationAdapter.class);
        DalOperationAdapter<?> agentAdapter = mock(DalOperationAdapter.class);

        registry.register(REST, "myDal", restAdapter);
        registry.register(AGENT, "myDal", agentAdapter);

        assertSame(restAdapter, registry.get(REST, "myDal"));
        assertSame(agentAdapter, registry.get(AGENT, "myDal"));
    }

    @Test
    void register_whenCalledTwice_firstRegistrationWins() {
        DalOperationAdapter<?> first = mock(DalOperationAdapter.class);
        DalOperationAdapter<?> second = mock(DalOperationAdapter.class);

        registry.register(REST, "myDal", first);
        registry.register(REST, "myDal", second);

        assertSame(first, registry.get(REST, "myDal"), "First registration should win");
    }

    @Test
    void channelsWithTheSameId_shouldShareTheRegistration() {
        DalOperationAdapter<?> adapter = mock(DalOperationAdapter.class);

        registry.register(DalChannel.of("rest"), "myDal", adapter);

        assertSame(adapter, registry.get(REST, "myDal"));
    }
}
