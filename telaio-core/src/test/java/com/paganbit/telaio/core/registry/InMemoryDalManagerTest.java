package com.paganbit.telaio.core.registry;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InMemoryDalManagerTest {

    @Mock
    private ListableBeanFactory beanFactory;

    private InMemoryDalManager registryManager;

    @BeforeEach
    void setUp() {
        registryManager = new InMemoryDalManager(beanFactory);
    }

    @Test
    void shouldRegisterServiceSuccessfully() {
        // Given
        String dalName = "testDal";
        DalDefinitionEntry definition = mock(DalDefinitionEntry.class);
        when(definition.name()).thenReturn(dalName);
        Dal<?, ?> service = mock(Dal.class);
        when(beanFactory.getBean(dalName, Dal.class)).thenReturn(service);

        // When
        registryManager.register(definition);

        // Then
        Dal<?, ?> retrievedService = registryManager.getServiceByName(dalName);
        DalDefinitionEntry retrievedDefinition = registryManager.getDefinitionByName(dalName);

        assertSame(service, retrievedService);
        assertSame(definition, retrievedDefinition);
    }

    @Test
    void shouldNotReplaceExistingDefinition() {
        // Given
        String dalName = "testDal";

        DalDefinitionEntry definition1 = mock(DalDefinitionEntry.class);
        when(definition1.name()).thenReturn(dalName);

        // Register the first service
        registryManager.register(definition1);

        // When
        // Create a second definition with the same name
        DalDefinitionEntry definition2 = mock(DalDefinitionEntry.class);
        when(definition2.name()).thenReturn(dalName);

        // Attempt to register the second service with the same name
        registryManager.register(definition2);

        // Then
        // Verify that the original definition is still in the registry
        DalDefinitionEntry retrievedDefinition = registryManager.getDefinitionByName(dalName);
        assertSame(definition1, retrievedDefinition);
        // Verify that the second definition was not stored
        assertNotSame(definition2, retrievedDefinition);
    }

    @Test
    void shouldGetServiceByName() {
        // Given
        String dalName = "testDal";
        DalDefinitionEntry definition = mock(DalDefinitionEntry.class);
        when(definition.name()).thenReturn(dalName);
        Dal<?, ?> service = mock(Dal.class);
        when(beanFactory.getBean(dalName, Dal.class)).thenReturn(service);

        registryManager.register(definition);

        // When
        Dal<?, ?> retrievedService = registryManager.getServiceByName(dalName);

        // Then
        assertSame(service, retrievedService);
        verify(beanFactory).getBean(dalName, Dal.class);
    }

    @Test
    void shouldThrowExceptionWhenServiceNotFound() {
        // Given
        String nonExistentDalName = "nonExistentDal";
        when(beanFactory.getBean(nonExistentDalName, Dal.class))
            .thenThrow(new NoSuchBeanDefinitionException(nonExistentDalName));

        // When / Then
        assertThrows(NoSuchBeanDefinitionException.class, () -> registryManager.getServiceByName(nonExistentDalName));
        verify(beanFactory).getBean(nonExistentDalName, Dal.class);
    }

    @Test
    void shouldGetDefinitionByName() {
        // Given
        String dalName = "testDal";
        DalDefinitionEntry definition = mock(DalDefinitionEntry.class);
        when(definition.name()).thenReturn(dalName);

        registryManager.register(definition);

        // When
        DalDefinitionEntry retrievedDefinition = registryManager.getDefinitionByName(dalName);

        // Then
        assertSame(definition, retrievedDefinition);
    }

    @Test
    void shouldThrowExceptionWhenDefinitionNotFound() {
        // Given
        String nonExistentDalName = "nonExistentDal";

        // When / Then
        assertThrows(DalNotFoundException.class, () -> registryManager.getDefinitionByName(nonExistentDalName));
    }

    @Test
    void shouldReturnAllDefinitions() {
        // Given
        DalDefinitionEntry definition1 = mock(DalDefinitionEntry.class);
        when(definition1.name()).thenReturn("dal1");
        DalDefinitionEntry definition2 = mock(DalDefinitionEntry.class);
        when(definition2.name()).thenReturn("dal2");

        registryManager.register(definition1);
        registryManager.register(definition2);

        // When
        Collection<DalDefinitionEntry> all = registryManager.getAllDefinitions();

        // Then
        assertEquals(2, all.size());
        assertTrue(all.contains(definition1));
        assertTrue(all.contains(definition2));
    }

    @Test
    void shouldReturnEmptyCollectionWhenNoDefinitions() {
        // When
        Collection<DalDefinitionEntry> all = registryManager.getAllDefinitions();

        // Then
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    // Test adapter interface and implementation for testing getAdapter method
    interface TestAdapter {
    }

    static class TestAdapterImpl implements TestAdapter {
    }

    @Test
    void shouldGetAdapterUsingDirectBeanLookup() {
        // Given
        TestAdapterImpl adapter = new TestAdapterImpl();
        when(beanFactory.getBean(TestAdapterImpl.class)).thenReturn(adapter);

        // When
        TestAdapter result = registryManager.getAdapter(TestAdapterImpl.class);

        // Then
        assertSame(adapter, result);
        verify(beanFactory).getBean(TestAdapterImpl.class);
    }

    @Test
    void shouldGetAdapterUsingTypeLookup() {
        // Given
        TestAdapterImpl adapter = new TestAdapterImpl();

        // Mock the direct lookup to fail
        when(beanFactory.getBean(TestAdapterImpl.class))
            .thenThrow(new NoSuchBeanDefinitionException(TestAdapterImpl.class));

        // Mock the type-based lookup to succeed
        Map<String, TestAdapterImpl> adapters = new HashMap<>();
        adapters.put("testAdapter", adapter);
        when(beanFactory.getBeansOfType(TestAdapterImpl.class)).thenReturn(adapters);

        // When
        TestAdapter result = registryManager.getAdapter(TestAdapterImpl.class);

        // Then
        assertSame(adapter, result);
        verify(beanFactory).getBean(TestAdapterImpl.class);
        verify(beanFactory).getBeansOfType(TestAdapterImpl.class);
    }

    @Test
    void shouldThrowExceptionWhenAdapterNotFound() {
        // Given
        // Mock the direct lookup to fail
        when(beanFactory.getBean(TestAdapterImpl.class))
            .thenThrow(new NoSuchBeanDefinitionException(TestAdapterImpl.class));

        // Mock the type-based lookup to return an empty map
        when(beanFactory.getBeansOfType(TestAdapterImpl.class)).thenReturn(new HashMap<>());

        // When / Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> registryManager.getAdapter(TestAdapterImpl.class));

        // Verify the exception message contains the expected information
        assertTrue(exception.getMessage().contains("No exact bean match found for adapter class"));
        assertTrue(exception.getMessage().contains(TestAdapterImpl.class.getName()));

        // Verify the correct methods were called
        verify(beanFactory).getBean(TestAdapterImpl.class);
        verify(beanFactory).getBeansOfType(TestAdapterImpl.class);
    }
}
