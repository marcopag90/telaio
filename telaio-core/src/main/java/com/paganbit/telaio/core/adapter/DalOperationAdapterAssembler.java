package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.core.registry.DalManager;
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
 * {@link DalOperationAdapterRegistry}.
 *
 * <p>The base adapter (a {@link DelegatingDalOperationAdapter} over the {@link Dal} itself, exposing the
 * entity directly) is wrapped with the interceptors supplied by the available
 * {@link DalAdapterInterceptorProvider}s in {@code Ordered} order, lowest value outermost.
 * Internal DALs are skipped: they are never exposed on a remote boundary.</p>
 *
 * <p>Assembly runs once every singleton exists, so providers contributed by any module are collected
 * regardless of the order in which their auto-configurations were processed.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public class DalOperationAdapterAssembler implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DalOperationAdapterAssembler.class);

    private final DalManager dalManager;
    private final DalOperationAdapterRegistry registry;
    private final List<DalAdapterInterceptorProvider> interceptorProviders;

    public DalOperationAdapterAssembler(
        DalManager dalManager,
        DalOperationAdapterRegistry registry,
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
                log.debug("DAL '{}' is internal — not exposed on any remote boundary", entry.name());
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
        return new DelegatingDalOperationAdapter(dalService);
    }
}
