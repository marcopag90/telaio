package com.paganbit.telaio.metrics.annotation;

import com.paganbit.telaio.core.adapter.DalOperationType;

import java.lang.annotation.*;

/**
 * Tunes metrics collection for a DAL service.
 *
 * <p>Unlike auditing, a metrics collection is on by default for every DAL — performance problems
 * tend to appear exactly where nobody thought to opt in. This annotation is therefore an
 * override: use {@code @DalMetrics(enabled = false)} to exclude a DAL, or restrict
 * {@link #operations()} to measure a subset. The global switch is the
 * {@code telaio.metrics.enabled} property.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DalMetrics {

    /**
     * Whether metrics are collected for this DAL.
     */
    boolean enabled() default true;

    /**
     * The operations to measure. When empty (the default), all operations are measured.
     */
    DalOperationType[] operations() default {};
}
