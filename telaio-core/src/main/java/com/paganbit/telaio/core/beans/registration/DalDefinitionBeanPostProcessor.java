package com.paganbit.telaio.core.beans.registration;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.exception.DalDefinitionException;
import com.paganbit.telaio.core.registry.DalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

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
                log.debug("Registering DAL bean with name: {} (internal={})",
                    definition.name(), definition.internal());
                dalManager.register(DalServiceDefinitions.entryOf(definition, dalClass));
            } catch (Exception e) {
                throw new DalDefinitionException(
                    "Failed to register DAL bean for class: %s".formatted(bean.getClass().getName()), e
                );
            }
        }
        return bean;
    }
}
