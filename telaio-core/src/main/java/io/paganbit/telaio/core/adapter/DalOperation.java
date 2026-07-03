package io.paganbit.telaio.core.adapter;

import java.lang.annotation.*;

/**
 * Marks a {@link io.paganbit.telaio.core.Dal} or {@link DalOperationAdapter} method with its
 * {@link DalOperationType}.
 *
 * <p>Used by interceptors to dispatch cross-cutting logic per operation without relying on
 * method names. Interceptors should resolve the annotation with
 * {@link org.springframework.core.annotation.AnnotationUtils#findAnnotation(java.lang.reflect.Method, Class)}
 * so declarations on interface methods are visible through concrete implementations.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DalOperation {

    DalOperationType value();
}
