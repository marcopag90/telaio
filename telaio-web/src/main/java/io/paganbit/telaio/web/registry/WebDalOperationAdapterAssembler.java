package io.paganbit.telaio.web.registry;

import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.adapter.DalAdapterContext;
import io.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import io.paganbit.telaio.core.adapter.DalOperationAdapter;
import io.paganbit.telaio.core.registry.DalDefinitionEntry;
import io.paganbit.telaio.core.registry.DalManager;
import io.paganbit.telaio.web.adapter.WebDalOperationAdapter;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the {@link DalOperationAdapter} of each registered DAL and publishes it to the
 * {@link WebDalOperationAdapterRegistry}.
 *
 * <p>The base adapter (the {@link Dal} itself, exposing the entity directly) is wrapped with the
 * interceptors supplied by the available {@link DalAdapterInterceptorProvider}s, in their declared
 * order.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class WebDalOperationAdapterAssembler implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(WebDalOperationAdapterAssembler.class);

    private final DalManager dalManager;
    private final WebDalOperationAdapterRegistry registry;
    private final List<DalAdapterInterceptorProvider> interceptorProviders;

    public WebDalOperationAdapterAssembler(
        DalManager dalManager,
        WebDalOperationAdapterRegistry registry,
        List<DalAdapterInterceptorProvider> interceptorProviders
    ) {
        this.dalManager = dalManager;
        this.registry = registry;
        this.interceptorProviders = new ArrayList<>(interceptorProviders);
        AnnotationAwareOrderComparator.sort(this.interceptorProviders);
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (DalDefinitionEntry entry : dalManager.getAllDefinitions()) {
            if (entry.internal()) {
                log.debug("DAL '{}' is internal — not exposed via REST", entry.name());
                continue;
            }
            log.debug("Assembling operation adapter for DAL: {}", entry.name());
            registry.register(entry.name(), assemble(entry));
        }
    }

    private DalOperationAdapter<?, ?> assemble(DalDefinitionEntry entry) {
        DalOperationAdapter<?, ?> base = buildBase(entry);

        ProxyFactory proxyFactory = new ProxyFactory(base);
        proxyFactory.setInterfaces(DalOperationAdapter.class);

        DalAdapterContext context = new DalAdapterContext(entry.name(), entry.dalClass(), dalManager);
        for (DalAdapterInterceptorProvider provider : interceptorProviders) {
            MethodInterceptor interceptor = provider.getInterceptor(context);
            if (interceptor != null) {
                proxyFactory.addAdvice(interceptor);
            }
        }
        return (DalOperationAdapter<?, ?>) proxyFactory.getProxy();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private DalOperationAdapter<?, ?> buildBase(DalDefinitionEntry entry) {
        Dal<?, ?> dalService = dalManager.getServiceByName(entry.name());
        return new WebDalOperationAdapter(dalService);
    }
}
