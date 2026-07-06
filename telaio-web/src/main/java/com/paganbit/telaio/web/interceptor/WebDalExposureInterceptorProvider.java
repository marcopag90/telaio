package com.paganbit.telaio.web.interceptor;

import com.paganbit.telaio.core.adapter.DalAdapterContext;
import com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Contributes the {@link WebDalExposureInterceptor} for DALs that expose only a subset of the CRUD
 * operations.
 *
 * <p>Returns {@code null} for DALs that expose the full surface, so the common case adds no interceptor.
 * Ordered as {@link DalAdapterInterceptorProvider#EXPOSURE_PRECEDENCE} — the outermost interceptor, so a
 * call to a non-exposed operation is rejected before audit, security, RBAC or the
 * {@link com.paganbit.telaio.core.Dal} runs. Internal DALs never reach this provider: they are skipped
 * during adapter assembly.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class WebDalExposureInterceptorProvider implements DalAdapterInterceptorProvider {

    private static final Set<DalOperationType> ALL_OPERATIONS = EnumSet.allOf(DalOperationType.class);

    @Override
    public @Nullable MethodInterceptor getInterceptor(DalAdapterContext context) {
        DalDefinitionEntry entry = context.dalManager().getDefinitionByName(context.dalName());
        Set<DalOperationType> exposed = entry.exposedOperations();
        if (exposed.containsAll(ALL_OPERATIONS)) {
            return null;
        }
        return new WebDalExposureInterceptor(context.dalName(), exposed);
    }

    @Override
    public int getOrder() {
        return EXPOSURE_PRECEDENCE;
    }
}
