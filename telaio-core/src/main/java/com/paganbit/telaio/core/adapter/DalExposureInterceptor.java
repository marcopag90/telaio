package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.exception.DalOperationNotExposedException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Rejects calls to CRUD operations a DAL does not expose on the channel its adapter chain belongs to.
 *
 * <p>Each operation is identified through its {@link DalOperation} annotation and checked against the
 * exposed-operation set the channel's {@link com.paganbit.telaio.core.channel.DalChannelDefinition}
 * declared at assembly time. An exposed operation passes through; a non-exposed one raises a
 * {@link DalOperationNotExposedException} carrying the DAL, the operation and the channel. Methods
 * without a {@link DalOperation} annotation pass through unchanged, unless they belong to
 * {@link DalOperationAdapter}, where a missing annotation is a programming error.</p>
 *
 * <p>The {@link DalOperationAdapterAssembler} places this interceptor outermost in every chain
 * ({@link DalAdapterInterceptorProvider#EXPOSURE_PRECEDENCE}), so a rejected call never reaches audit,
 * security or the {@link com.paganbit.telaio.core.Dal} itself.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public class DalExposureInterceptor implements MethodInterceptor {

    private final String dalName;
    private final DalChannel channel;
    private final Set<DalOperationType> exposedOperations;

    /**
     * Creates the interceptor guarding one chain.
     *
     * @param dalName           the registration name of the DAL
     * @param channel           the channel of the adapter chain this interceptor guards
     * @param exposedOperations the operations the DAL exposes on that channel; copied
     */
    public DalExposureInterceptor(String dalName, DalChannel channel, Set<DalOperationType> exposedOperations) {
        this.dalName = dalName;
        this.channel = channel;
        this.exposedOperations = exposedOperations.isEmpty()
            ? EnumSet.noneOf(DalOperationType.class)
            : EnumSet.copyOf(exposedOperations);
    }

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        DalOperation op = invocation.getMethod().getAnnotation(DalOperation.class);
        if (op == null) {
            if (DalOperationAdapter.class.isAssignableFrom(invocation.getMethod().getDeclaringClass())) {
                throw new IllegalStateException(
                    "Method '" + invocation.getMethod().getName() + "' on DalOperationAdapter " +
                        "is missing @DalOperation — add the annotation so the exposure interceptor can apply");
            }
            return invocation.proceed();
        }
        if (!exposedOperations.contains(op.value())) {
            throw new DalOperationNotExposedException(dalName, op.value(), channel);
        }
        return invocation.proceed();
    }
}
