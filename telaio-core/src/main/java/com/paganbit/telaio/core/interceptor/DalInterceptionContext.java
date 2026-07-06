package com.paganbit.telaio.core.interceptor;

import com.paganbit.telaio.core.Dal;

/**
 * Supplies a {@link DalInterceptorProvider} with the details of the {@link Dal} bean being
 * processed.
 *
 * @param dalName      the registration name of the DAL
 * @param dalBeanClass the user-declared class of the {@link Dal} bean (never a proxy class)
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalInterceptionContext(String dalName, Class<? extends Dal<?, ?>> dalBeanClass) {
}
