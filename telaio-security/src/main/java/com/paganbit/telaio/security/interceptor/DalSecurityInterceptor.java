package com.paganbit.telaio.security.interceptor;

import com.paganbit.telaio.core.adapter.DalOperation;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.security.DalSecurityContextHelper;
import com.paganbit.telaio.security.adapter.DalAuthAdapter;
import com.paganbit.telaio.security.adapter.DalRbacAdapter;
import com.paganbit.telaio.security.exception.DalAccessDeniedException;
import com.paganbit.telaio.security.exception.DalAccessDeniedMessageResolver;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies a DAL's authorization and RBAC policy to its operation adapter.
 *
 * <p>Each operation is identified via its {@link DalOperation} annotation — authorization is
 * checked through the {@link DalAuthAdapter} and input/output are filtered through the
 * {@link DalRbacAdapter}. Methods without a {@link DalOperation} annotation are passed through
 * unchanged. The {@link Authentication} is read from the current Spring Security context.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class DalSecurityInterceptor implements MethodInterceptor {

    private final String dalName;
    private final DalAuthAdapter authAdapter;
    private final DalRbacAdapter rbacAdapter;
    private final DalAccessDeniedMessageResolver messageResolver;

    public DalSecurityInterceptor(
        String dalName,
        DalAuthAdapter authAdapter,
        DalRbacAdapter rbacAdapter,
        DalAccessDeniedMessageResolver messageResolver
    ) {
        this.dalName = dalName;
        this.authAdapter = authAdapter;
        this.rbacAdapter = rbacAdapter;
        this.messageResolver = messageResolver;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        DalOperation op = invocation.getMethod().getAnnotation(DalOperation.class);
        if (op == null) {
            if (DalOperationAdapter.class.isAssignableFrom(invocation.getMethod().getDeclaringClass())) {
                throw new IllegalStateException(
                    "Method '" + invocation.getMethod().getName() + "' on DalOperationAdapter " +
                        "is missing @DalOperation — add the annotation so the security interceptor can apply");
            }
            return invocation.proceed();
        }

        Authentication auth = DalSecurityContextHelper.getCurrentAuthentication();
        Object[] args = invocation.getArguments();

        return switch (op.value()) {
            case CREATE -> {
                require(authAdapter.authorizeCreate(auth), messageResolver.forCreate(dalName));
                args[0] = rbacAdapter.filterInput(DalOperationType.CREATE, (Map<String, Object>) args[0], auth);
                yield rbacAdapter.filterOutput(DalOperationType.CREATE, invocation.proceed(), auth);
            }
            case READ -> {
                require(authAdapter.authorizeRead(auth), messageResolver.forRead(dalName));
                Page page = Objects.requireNonNull((Page) invocation.proceed());
                yield page.map(dto -> rbacAdapter.filterOutput(DalOperationType.READ, dto, auth));
            }
            case READ_ONE -> {
                Object id = args[0];
                require(authAdapter.authorizeReadOne(auth, id), messageResolver.forReadOne(dalName, id));
                yield rbacAdapter.filterOutput(DalOperationType.READ_ONE, invocation.proceed(), auth);
            }
            case UPDATE -> {
                Object id = args[0];
                require(authAdapter.authorizeUpdate(auth, id), messageResolver.forUpdate(dalName, id));
                args[1] = rbacAdapter.filterInput(DalOperationType.UPDATE, (Map<String, Object>) args[1], auth);
                Optional<Object> updated = Objects.requireNonNull((Optional<Object>) invocation.proceed());
                yield updated.map(dto -> rbacAdapter.filterOutput(DalOperationType.UPDATE, dto, auth));
            }
            case DELETE -> {
                Object id = args[0];
                require(authAdapter.authorizeDelete(auth, id), messageResolver.forDelete(dalName, id));
                yield invocation.proceed();
            }
        };
    }

    private static void require(boolean authorized, String message) {
        if (!authorized) {
            throw new DalAccessDeniedException(message);
        }
    }
}
