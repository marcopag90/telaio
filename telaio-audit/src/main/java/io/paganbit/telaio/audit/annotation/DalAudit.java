package io.paganbit.telaio.audit.annotation;

import io.paganbit.telaio.core.adapter.DalOperationType;

import java.lang.annotation.*;

/**
 * Enables auditing for a DAL service.
 *
 * <p>Applied alongside {@code @DalService}. Auditing is an opt-in: DALs without this annotation are
 * never audited. Each audited invocation is recorded as a
 * {@link io.paganbit.telaio.audit.event.DalAuditEvent} through the configured
 * {@link io.paganbit.telaio.audit.event.DalAuditEventStore}.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DalAudit {

    /**
     * The operations to audit. When empty (the default), all operations are audited — removing
     * the annotation is the only way to disable auditing for a DAL.
     */
    DalOperationType[] operations() default {};
}
