package io.paganbit.telaio.core.beans.registration;

import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.core.interceptor.DalInterceptionContext;
import io.paganbit.telaio.core.interceptor.DalInterceptorProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Objects;

/**
 * Wraps {@link Dal} beans in a proxy carrying the interceptors contributed by the registered
 * {@link DalInterceptorProvider} beans.
 *
 * <p>Because the proxy is applied to the {@link Dal} bean itself, the contributed interceptors
 * apply to every invocation channel — web adapters, messaging consumers, or plain programmatic
 * calls. Beans for which no provider contributes an interceptor are returned unchanged.</p>
 *
 * <p>The proxy is class-based (CGLIB), so injection points typed to the concrete service class keep
 * working. As with any Spring proxy, self-invocations inside the DAL bypass the interceptors.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalInterceptionBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DalInterceptionBeanPostProcessor.class);

    private final ObjectProvider<DalInterceptorProvider> interceptorProviders;

    private @Nullable List<DalInterceptorProvider> resolvedProviders;

    public DalInterceptionBeanPostProcessor(ObjectProvider<DalInterceptorProvider> interceptorProviders) {
        this.interceptorProviders = interceptorProviders;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof Dal<?, ?>)) {
            return bean;
        }
        final var dalBeanClass = (Class<? extends Dal<?, ?>>) ClassUtils.getUserClass(bean.getClass());
        final var definition = AnnotationUtils.findAnnotation(dalBeanClass, DalService.class);
        if (definition == null) {
            return bean;
        }

        final var context = new DalInterceptionContext(definition.name(), dalBeanClass);
        final var interceptors = providers().stream()
            .map(provider -> provider.getInterceptor(context))
            .filter(Objects::nonNull)
            .toList();
        if (interceptors.isEmpty()) {
            return bean;
        }

        log.debug("Applying {} DAL interceptor(s) to bean: {}", interceptors.size(), beanName);
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(true);
        interceptors.forEach(proxyFactory::addAdvice);
        return proxyFactory.getProxy(dalBeanClass.getClassLoader());
    }

    private List<DalInterceptorProvider> providers() {
        /*
         Resolved lazily on the first Dal bean, so provider beans are not forced to initialize
         during BeanPostProcessor registration.
         */
        if (resolvedProviders == null) {
            resolvedProviders = interceptorProviders.stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .toList();
        }
        return resolvedProviders;
    }
}
