package io.paganbit.telaio.core.adapter;

import io.paganbit.telaio.core.Dal;
import io.paganbit.telaio.core.registry.DalManager;

/**
 * Supplies a {@link DalAdapterInterceptorProvider} with the details of the DAL being assembled.
 *
 * @param dalName      the registration name of the DAL
 * @param dalBeanClass the concrete class of the {@link Dal} bean
 * @param dalManager   the registry used to resolve services and adapter beans
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalAdapterContext(String dalName, Class<?> dalBeanClass, DalManager dalManager) {
}
