package io.paganbit.telaio.web.interceptor;

import io.paganbit.telaio.core.adapter.DalOperation;
import io.paganbit.telaio.core.adapter.DalOperationAdapter;
import io.paganbit.telaio.core.adapter.DalOperationType;
import io.paganbit.telaio.web.exception.DalOperationNotExposedException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.http.HttpMethod;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rejects calls to CRUD operations a DAL does not expose on the remote boundary.
 *
 * <p>Each operation is identified via its {@link DalOperation} annotation and checked against the DAL's
 * exposed-operation set. An exposed operation passes through; a non-exposed one raises a
 * {@link DalOperationNotExposedException}, computing whether the target URI still has other exposed
 * operations (→ {@code 405} with an {@code Allow} header) or none (→ {@code 404}). Methods without a
 * {@link DalOperation} annotation pass through unchanged.</p>
 *
 * <p>As the outermost interceptor ({@link io.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider#EXPOSURE_PRECEDENCE}),
 * it short-circuits before audit, security, RBAC or the {@link io.paganbit.telaio.core.Dal} itself.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class WebDalExposureInterceptor implements MethodInterceptor {

    /**
     * Operations served by the collection URI {@code /dal/v1/<name>}.
     */
    private static final Set<DalOperationType> COLLECTION_OPERATIONS =
        EnumSet.of(DalOperationType.CREATE, DalOperationType.READ);

    /**
     * Operations served by the item URI {@code /dal/v1/<name>/{id}}.
     */
    private static final Set<DalOperationType> ITEM_OPERATIONS =
        EnumSet.of(DalOperationType.READ_ONE, DalOperationType.UPDATE, DalOperationType.DELETE);

    private static final Map<DalOperationType, HttpMethod> HTTP_METHODS = Map.of(
        DalOperationType.CREATE, HttpMethod.POST,
        DalOperationType.READ, HttpMethod.GET,
        DalOperationType.READ_ONE, HttpMethod.GET,
        DalOperationType.UPDATE, HttpMethod.PATCH,
        DalOperationType.DELETE, HttpMethod.DELETE
    );

    private final String dalName;
    private final Set<DalOperationType> exposedOperations;

    public WebDalExposureInterceptor(String dalName, Set<DalOperationType> exposedOperations) {
        this.dalName = dalName;
        this.exposedOperations = EnumSet.copyOf(exposedOperations);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        DalOperation op = invocation.getMethod().getAnnotation(DalOperation.class);
        if (op == null) {
            if (DalOperationAdapter.class.isAssignableFrom(invocation.getMethod().getDeclaringClass())) {
                throw new IllegalStateException(
                    "Method '" + invocation.getMethod().getName() + "' on DalOperationAdapter " +
                        "is missing @DalOperation — add the annotation so the exposure interceptor can apply");
            }
            return invocation.proceed();
        }

        DalOperationType operation = op.value();
        if (exposedOperations.contains(operation)) {
            return invocation.proceed();
        }

        final var uriGroup = COLLECTION_OPERATIONS.contains(operation) ? COLLECTION_OPERATIONS : ITEM_OPERATIONS;
        Set<HttpMethod> allowed = new LinkedHashSet<>();
        for (DalOperationType groupOperation : uriGroup) {
            if (exposedOperations.contains(groupOperation)) {
                allowed.add(HTTP_METHODS.get(groupOperation));
            }
        }
        throw new DalOperationNotExposedException(dalName, operation, allowed, allowed.isEmpty());
    }
}
