package com.paganbit.telaio.security.interceptor;

import com.paganbit.telaio.core.adapter.DalAdapterContext;
import com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.registry.DalAdapterRegistry;
import com.paganbit.telaio.security.adapter.*;
import com.paganbit.telaio.security.annotation.DalSecurity;
import com.paganbit.telaio.security.exception.DalAccessDeniedMessageResolver;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.function.Predicate;

/**
 * Contributes the authorization and RBAC interceptor for each DAL.
 *
 * <p>The adapters are taken from the DAL's {@link DalSecurity} when present; otherwise
 * {@link PermitAllDalAuthAdapter} and {@link NoopDalRbacAdapter} apply, so a DAL declared without
 * {@code @DalSecurity} is open to any authenticated principal. Note that a bare {@code @DalSecurity()}
 * still defaults its {@code authAdapterClass} to {@link DenyAllDalAuthAdapter}: opting in to the
 * annotation is secure-by-default, while omitting it altogether leaves the DAL open.
 * Ordered as {@link DalAdapterInterceptorProvider#SECURITY_PRECEDENCE}. The policy is the same on every
 * channel: authorization answers <em>who</em> may call, never <em>from where</em>.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DalSecurityInterceptorProvider implements DalAdapterInterceptorProvider {

    private final DalAccessDeniedMessageResolver messageResolver;
    private final JsonPropertyPathResolver pathResolver;
    private final DalAdapterRegistry adapterRegistry;

    /**
     * @param messageResolver the resolver of the (server-side only) denial messages
     * @param pathResolver    the resolver telling whether a filter or sort path exists on the entity
     * @param adapterRegistry the registry resolving the {@link DalAuthAdapter} and {@link DalRbacAdapter}
     *                        beans named by {@link DalSecurity}
     */
    public DalSecurityInterceptorProvider(
        DalAccessDeniedMessageResolver messageResolver,
        JsonPropertyPathResolver pathResolver,
        DalAdapterRegistry adapterRegistry
    ) {
        this.messageResolver = messageResolver;
        this.pathResolver = pathResolver;
        this.adapterRegistry = adapterRegistry;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public MethodInterceptor getInterceptor(@NonNull DalAdapterContext context) {
        DalSecurity definition =
            AnnotationUtils.findAnnotation(context.dalBeanClass(), DalSecurity.class);

        Class<? extends DalAuthAdapter> authClass =
            definition != null ? definition.authAdapterClass() : PermitAllDalAuthAdapter.class;
        Class<? extends DalRbacAdapter> rbacClass =
            definition != null ? definition.rbacAdapterClass() : NoopDalRbacAdapter.class;

        DalAuthAdapter authAdapter = adapterRegistry.getAdapter(authClass);
        DalRbacAdapter rbacAdapter = adapterRegistry.getAdapter(rbacClass);

        return new DalSecurityInterceptor(
            context.dalName(),
            authAdapter,
            rbacAdapter,
            messageResolver,
            entityFieldExists(context)
        );
    }

    private Predicate<String> entityFieldExists(DalAdapterContext context) {
        Class<?> entityClass = context.catalog().getServiceByName(context.dalName()).getEntityClass();
        return path -> pathResolver.resolveJavaPath(entityClass, path).resolved();
    }

    @Override
    public int getOrder() {
        return SECURITY_PRECEDENCE;
    }
}
