package com.paganbit.telaio.security.interceptor;

import com.paganbit.telaio.core.adapter.DalOperation;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.exception.DalFilterFieldNotReadableException;
import com.paganbit.telaio.core.exception.DalSortFieldNotReadableException;
import com.paganbit.telaio.core.filter.FilterNodes;
import com.paganbit.telaio.security.DalSecurityContextHelper;
import com.paganbit.telaio.security.adapter.DalAuthAdapter;
import com.paganbit.telaio.security.adapter.DalRbacAdapter;
import com.paganbit.telaio.security.exception.DalAccessDeniedException;
import com.paganbit.telaio.security.exception.DalAccessDeniedMessageResolver;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Applies a DAL's authorization and RBAC policy to its operation adapter.
 *
 * <p>Each operation is identified via its {@link DalOperation} annotation and goes through, in order:</p>
 * <ol>
 *   <li>authorization, via the {@link DalAuthAdapter};</li>
 *   <li>for reads with a filter, a {@link DalRbacAdapter#canFilterOn} check on every referenced field —
 *       a field the principal may not read rejects the request with a
 *       {@link DalFilterFieldNotReadableException} (on the wire the same client fault as an unknown
 *       field; for audit a denied attempt);</li>
 *   <li>for reads with a sort, the same {@link DalRbacAdapter#canFilterOn} check on every
 *       {@link Sort.Order} property — a sort key the principal may not read rejects the request with a
 *       {@link DalSortFieldNotReadableException} (on the wire the same client fault as an unknown sort
 *       property; for audit a denied attempt);</li>
 *   <li>input/output filtering, via the {@link DalRbacAdapter}.</li>
 * </ol>
 *
 * <p>Methods without a {@link DalOperation} annotation are passed through unchanged. The
 * {@link Authentication} is read from the current Spring Security context.</p>
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
    private final Predicate<String> entityFieldExists;

    public DalSecurityInterceptor(
        String dalName,
        DalAuthAdapter authAdapter,
        DalRbacAdapter rbacAdapter,
        DalAccessDeniedMessageResolver messageResolver,
        Predicate<String> entityFieldExists
    ) {
        this.dalName = dalName;
        this.authAdapter = authAdapter;
        this.rbacAdapter = rbacAdapter;
        this.messageResolver = messageResolver;
        this.entityFieldExists = entityFieldExists;
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
                requireReadableFilterFields((FilterNode) args[0], auth);
                requireReadableSortProperties((Pageable) args[1], auth);
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

    /**
     * Rejects a read filter that references a field the principal may not read — checked after the
     * operation-level authorization (a denied read stays a {@code 403}) and before the read runs, so no
     * query is ever executed on a hidden property. A rejected field that does not exist on the entity
     * falls through instead: the read's own strict validation rejects it as a plain client fault.
     */
    private void requireReadableFilterFields(@Nullable FilterNode filter, Authentication auth) {
        if (filter == null) {
            return;
        }
        for (FieldNode field : FilterNodes.fieldNodes(filter)) {
            if (!rbacAdapter.canFilterOn(field.getName(), auth) && entityFieldExists.test(field.getName())) {
                throw new DalFilterFieldNotReadableException(field.getName());
            }
        }
    }

    /**
     * Rejects a sort that references a property the principal may not read — the rule is identical to
     * the filter-field check ({@link DalRbacAdapter#canFilterOn}): a sort on a hidden property leaks the
     * relative order of its values. Checked after the operation-level authorization (a denied read
     * stays a {@code 403}) and before the read runs, so no query is ever ordered by a hidden property.
     * A rejected key that does not exist on the entity falls through instead: the read's own strict
     * validation rejects it as a plain client fault.
     */
    private void requireReadableSortProperties(@Nullable Pageable pageable, Authentication auth) {
        if (pageable == null) {
            return;
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!rbacAdapter.canFilterOn(order.getProperty(), auth) && entityFieldExists.test(order.getProperty())) {
                throw new DalSortFieldNotReadableException(order.getProperty());
            }
        }
    }
}
