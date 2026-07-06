package com.paganbit.telaio.security.interceptor;

import com.paganbit.telaio.core.adapter.DalAdapterContext;
import com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import com.paganbit.telaio.security.adapter.*;
import com.paganbit.telaio.security.annotation.DalSecurity;
import com.paganbit.telaio.security.exception.DalAccessDeniedMessageResolver;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Contributes the authorization and RBAC interceptor for each DAL.
 *
 * <p>The adapters are taken from the DAL's {@link DalSecurity} when present; otherwise
 * {@link PermitAllDalAuthAdapter} and {@link NoopDalRbacAdapter} apply, so a DAL declared without
 * {@code @DalSecurity} is open to any authenticated principal. Note that a bare {@code @DalSecurity()}
 * still defaults its {@code authAdapterClass} to {@link DenyAllDalAuthAdapter}: opting in to the
 * annotation is secure-by-default, while omitting it altogether leaves the DAL open.
 * Ordered as {@link DalAdapterInterceptorProvider#SECURITY_PRECEDENCE}.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalSecurityInterceptorProvider implements DalAdapterInterceptorProvider {

    private final DalAccessDeniedMessageResolver messageResolver;

    public DalSecurityInterceptorProvider(DalAccessDeniedMessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public MethodInterceptor getInterceptor(DalAdapterContext context) {
        DalSecurity definition =
            AnnotationUtils.findAnnotation(context.dalBeanClass(), DalSecurity.class);

        Class<? extends DalAuthAdapter> authClass =
            definition != null ? definition.authAdapterClass() : PermitAllDalAuthAdapter.class;
        Class<? extends DalRbacAdapter> rbacClass =
            definition != null ? definition.rbacAdapterClass() : NoopDalRbacAdapter.class;

        DalAuthAdapter authAdapter = context.dalManager().getAdapter(authClass);
        DalRbacAdapter rbacAdapter = context.dalManager().getAdapter(rbacClass);

        return new DalSecurityInterceptor(context.dalName(), authAdapter, rbacAdapter, messageResolver);
    }

    @Override
    public int getOrder() {
        return SECURITY_PRECEDENCE;
    }
}
