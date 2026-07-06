package io.paganbit.telaio.core.registry;

import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.core.exception.DalNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the {@link DalManager}.
 *
 * <p>This registry stores {@link Dal} instances along with their corresponding
 * {@link DalDefinitionEntry}, identified by a unique name. DALs are typically registered
 * automatically via the {@link DalService} annotation during the application bootstrap phase.</p>
 *
 * <p>Additionally, this implementation provides dynamic resolution of DAL adapter beans by locating
 * the corresponding Spring beans at runtime based on their exact class type (see {@link #getAdapter}).</p>
 *
 * <p>Designed for fast, thread-safe, in-memory access without persistence.</p>
 *
 * @author Marco Pagan
 * @see DalService
 * @see DalDefinitionEntry
 * @see DalManager
 * @see DalAdapterRegistry
 * @since 1.0.0
 */
public class InMemoryDalManager implements DalManager {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDalManager.class);

    private final Map<String, DalDefinitionEntry> entries = new ConcurrentHashMap<>();
    private final ListableBeanFactory beanFactory;

    public InMemoryDalManager(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * Registers a DAL service definition in the registry.
     * <p>
     * This method stores the definition in an in-memory map using the definition's name as the key.
     * If a definition with the same name already exists, it will not be replaced (putIfAbsent).
     * </p>
     *
     * @param definition the DAL definition to register
     */
    @Override
    public void register(DalDefinitionEntry definition) {
        String name = definition.name();
        entries.putIfAbsent(name, definition);
    }

    /**
     * Retrieves a DAL service by its registered name.
     * <p>
     * This method uses the Spring bean factory to look up a bean with the given name
     * that implements the DalService interface.
     * </p>
     *
     * @param name the DAL name defined in the application context
     * @return the associated DalService
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException  if no bean with the given name is found
     * @throws org.springframework.beans.factory.BeanNotOfRequiredTypeException if the bean is not a DalService
     */
    @Override
    public Dal<?, ?> getServiceByName(String name) {
        return beanFactory.getBean(name, Dal.class);
    }

    /**
     * Retrieves a DAL definition by its registered name.
     * <p>
     * This method looks up the definition in the in-memory map using the given name as the key.
     * If no definition is found with the given name, a DalNotFoundException is thrown.
     * </p>
     *
     * @param name the DAL name defined in the application context
     * @return the associated DalRegistryDefinition
     * @throws DalNotFoundException if no definition with the given name is found
     */
    @Override
    public DalDefinitionEntry getDefinitionByName(String name) {
        DalDefinitionEntry definition = entries.get(name);
        if (definition == null) {
            throw new DalNotFoundException(name);
        }
        return definition;
    }

    /**
     * Returns all registered DAL definitions.
     *
     * @return an unmodifiable collection of all registered definitions
     */
    @Override
    public Collection<DalDefinitionEntry> getAllDefinitions() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /**
     * Retrieves an adapter bean by its class type.
     * <p>
     * This method first attempts to find a bean directly by its class type. If that fails,
     * it tries to find a bean that exactly matches the requested class (not a subclass).
     * </p>
     * <p>
     * The implementation uses two strategies:
     * <ol>
     *   <li>Direct lookup by class using {@code beanFactory.getBean(adapterClass)}</li>
     *   <li>If that fails, it gets all beans of the adapter type and filters for an exact class match</li>
     * </ol>
     *
     * @param adapterClass the class of the adapter to retrieve
     * @param <T>          the type of the adapter
     * @return the adapter bean
     * @throws IllegalStateException if no bean with the exact adapter class is found
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Class<? extends T> adapterClass) {
        try {
            // Search the bean by class
            return beanFactory.getBean(adapterClass);
        } catch (Exception ex) {
            // Find the exact match, not subclasses
            return beanFactory.getBeansOfType((Class<T>) adapterClass)
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().getClass().equals(adapterClass))
                .findFirst()
                .map(entry -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Resolved adapter bean '{}' for class '{}'", entry.getKey(), adapterClass.getName());
                    }
                    return entry.getValue();
                })
                .orElseThrow(() -> new IllegalStateException(
                    "No exact bean match found for adapter class: " + adapterClass.getName() +
                        ". Did you forget to declare your adapter as a Spring bean?"
                ));
        }
    }
}
