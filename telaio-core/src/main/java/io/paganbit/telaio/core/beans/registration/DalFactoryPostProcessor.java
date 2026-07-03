package io.paganbit.telaio.core.beans.registration;

import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.core.exception.DalDefinitionException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * A Spring {@link BeanFactoryPostProcessor} that validates DAL service bean definitions.
 *
 * <p>Ensures that all beans implementing {@link Dal} are annotated with
 * {@link DalService}. This validation happens at the bean factory level, before
 * beans are fully initialized.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String[] beanNamesForType = beanFactory.getBeanNamesForType(Dal.class);
        for (String beanName : beanNamesForType) {
            DalService annotation = beanFactory.findAnnotationOnBean(beanName, DalService.class);
            if (annotation == null) {
                throw new DalDefinitionException("Missing %s annotation on bean [%s]"
                    .formatted(DalService.class.getName(), beanName)
                );
            }
        }
    }
}
