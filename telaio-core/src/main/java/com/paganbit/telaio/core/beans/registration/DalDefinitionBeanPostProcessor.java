package com.paganbit.telaio.core.beans.registration;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.exception.DalDefinitionException;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.core.registry.DalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Registers beans annotated with {@link DalService} into the {@link DalManager}.
 *
 * <p>A bean carrying the annotation must implement {@link Dal}; otherwise a
 * {@link DalDefinitionException} is raised.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalDefinitionBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DalDefinitionBeanPostProcessor.class);

    private final DalManager dalManager;

    public DalDefinitionBeanPostProcessor(DalManager dalManager) {
        this.dalManager = dalManager;
    }

    /**
     * {@inheritDoc}
     *
     * @throws DalDefinitionException if the bean is annotated with {@link DalService} but
     *                                does not implement {@link Dal}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        final var definition = AnnotationUtils.findAnnotation(bean.getClass(), DalService.class);
        if (definition != null) {
            if (!(bean instanceof Dal<?, ?>)) {
                throw new DalDefinitionException(
                    "Bean [%s] is annotated with @%s but does not implement %s".formatted(
                        beanName,
                        DalService.class.getName(),
                        Dal.class.getName()
                    ));
            }
            try {
                /*
                 getUserClass keeps the registered class stable even if the bean was proxied
                 by another BeanPostProcessor (e.g., DalInterceptionBeanPostProcessor)
                 */
                final var dalClass = (Class<? extends Dal<?, ?>>) ClassUtils.getUserClass(bean.getClass());
                final var exposedOperations = resolveExposedOperations(definition);
                log.debug("Registering DAL bean with name: {} (internal={}, operations={})",
                    definition.name(), definition.internal(), exposedOperations);
                dalManager.register(
                    new DalDefinitionEntry(definition.name(), dalClass, definition.internal(), exposedOperations));
            } catch (Exception e) {
                throw new DalDefinitionException(
                    "Failed to register DAL bean for class: %s".formatted(bean.getClass().getName()), e
                );
            }
        }
        return bean;
    }

    /**
     * Builds the exposed-operation set from the annotation, warning about configurations that are likely
     * mistakes: declaring {@code operations} on an {@code internal} DAL (it has no remote boundary, so the
     * set is ignored), or exposing a write that cannot be paired with a read-by-id.
     */
    private Set<DalOperationType> resolveExposedOperations(DalService definition) {
        final var operations = definition.operations().length == 0
            ? EnumSet.noneOf(DalOperationType.class)
            : EnumSet.copyOf(Arrays.asList(definition.operations()));

        if (definition.internal() && !operations.equals(EnumSet.allOf(DalOperationType.class))) {
            log.debug("DAL '{}' is internal — its @DalService(operations=...) is ignored (no remote boundary)",
                definition.name());
        }
        if (!definition.internal()
            && (operations.contains(DalOperationType.UPDATE) || operations.contains(DalOperationType.DELETE))
            && !operations.contains(DalOperationType.READ_ONE)) {
            log.debug("DAL '{}' exposes UPDATE/DELETE without READ_ONE: callers cannot read back the entity by id",
                definition.name());
        }
        return operations;
    }
}
