package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.channel.DalChannelDefinition;
import com.paganbit.telaio.core.exception.DalDefinitionException;
import com.paganbit.telaio.core.registry.DalCatalog;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Assembles one {@link DalOperationAdapter} per (channel, DAL) and publishes it to the
 * {@link DalOperationAdapterRegistry}.
 *
 * <p>For every {@link DalChannelDefinition} present and every non-internal DAL, the base adapter (a
 * {@link DelegatingDalOperationAdapter} over the {@link Dal} itself) is wrapped with core's
 * {@link DalExposureInterceptor}, parameterized by the operations the definition exposes for that DAL,
 * and then with the interceptors supplied by the available {@link DalAdapterInterceptorProvider}s in
 * {@code Ordered} order, lowest value outermost. The exposure interceptor is always the outermost one.
 * Internal DALs are skipped, as are DALs a definition exposes nothing of: neither gets a chain on that
 * channel. Two channels never share a chain, so a provider's interceptor is created once per channel.</p>
 *
 * <p>Assembly runs once every singleton exists, so definitions and providers contributed by any module
 * are collected regardless of the order in which their auto-configurations were processed.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public class DalOperationAdapterAssembler implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DalOperationAdapterAssembler.class);

    private final DalCatalog catalog;
    private final DalOperationAdapterRegistry registry;
    private final List<DalChannelDefinition> channelDefinitions;
    private final List<DalAdapterInterceptorProvider> interceptorProviders;

    /**
     * @param catalog              the registry of DALs and their definitions
     * @param registry             where the assembled adapters are published
     * @param channelDefinitions   one definition per channel module present; empty when none is
     * @param interceptorProviders the providers contributing interceptors to every chain
     */
    public DalOperationAdapterAssembler(
        DalCatalog catalog,
        DalOperationAdapterRegistry registry,
        List<DalChannelDefinition> channelDefinitions,
        List<DalAdapterInterceptorProvider> interceptorProviders
    ) {
        this.catalog = catalog;
        this.registry = registry;
        this.channelDefinitions = List.copyOf(channelDefinitions);
        this.interceptorProviders = new ArrayList<>(interceptorProviders);
        AnnotationAwareOrderComparator.sort(this.interceptorProviders);
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (channelDefinitions.isEmpty()) {
            log.debug("No channel definition present — no operation adapter is assembled");
            return;
        }
        validateChannels();
        for (DalChannelDefinition channelDefinition : channelDefinitions) {
            for (DalDefinitionEntry entry : catalog.getAllDefinitions()) {
                assembleIfExposed(channelDefinition, entry);
            }
        }
    }

    /**
     * Rejects a misconfigured set of definitions before any chain is registered: two definitions for the
     * same channel, or a definition claiming the in-process channel, where no chain ever runs.
     */
    private void validateChannels() {
        Set<DalChannel> seen = new HashSet<>();
        for (DalChannelDefinition channelDefinition : channelDefinitions) {
            DalChannel channel = channelDefinition.channel();
            if (DalChannel.IN_PROCESS.equals(channel)) {
                throw new DalDefinitionException(
                    "Channel '%s' is reserved for direct calls and cannot be declared by a DalChannelDefinition"
                        .formatted(channel));
            }
            if (!seen.add(channel)) {
                throw new DalDefinitionException(
                    "Channel '%s' is declared by more than one DalChannelDefinition".formatted(channel));
            }
        }
    }

    private void assembleIfExposed(DalChannelDefinition channelDefinition, DalDefinitionEntry entry) {
        DalChannel channel = channelDefinition.channel();
        if (entry.internal()) {
            log.debug("DAL '{}' is internal — not on channel '{}'", entry.name(), channel);
            return;
        }
        Set<DalOperationType> exposed = channelDefinition.exposedOperations(entry.dalClass());
        if (exposed.isEmpty()) {
            log.debug("DAL '{}' exposes no operation on channel '{}' — no adapter assembled", entry.name(), channel);
            return;
        }
        log.debug("Assembling operation adapter for DAL '{}' on channel '{}' (operations={})",
            entry.name(), channel, exposed);
        registry.register(channel, entry.name(), assemble(entry, channel, exposed));
    }

    private DalOperationAdapter<?> assemble(
        DalDefinitionEntry entry,
        DalChannel channel,
        Set<DalOperationType> exposed
    ) {
        DalOperationAdapter<?> base = buildBase(entry);

        ProxyFactory proxyFactory = new ProxyFactory(base);
        proxyFactory.setInterfaces(DalOperationAdapter.class);
        proxyFactory.addAdvice(new DalExposureInterceptor(entry.name(), channel, exposed));

        DalAdapterContext context = DalAdapterContext.of(entry, channel, catalog);
        for (DalAdapterInterceptorProvider provider : interceptorProviders) {
            MethodInterceptor interceptor = provider.getInterceptor(context);
            if (interceptor != null) {
                proxyFactory.addAdvice(interceptor);
            }
        }
        return (DalOperationAdapter<?>) proxyFactory.getProxy();
    }

    private DalOperationAdapter<?> buildBase(DalDefinitionEntry entry) {
        Dal<?, ?> dalService = catalog.getServiceByName(entry.name());
        return new DelegatingDalOperationAdapter<>(dalService);
    }
}
