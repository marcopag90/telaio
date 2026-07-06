package com.paganbit.telaio.core.annotation;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.registry.DalRegistry;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * Declares a bean as a DAL service, registering it with the {@link DalRegistry} under the given name.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface DalService {

    /**
     * The name under which this DAL will be registered and exposed.
     * Must be unique within the application context.
     */
    @AliasFor(annotation = Service.class, attribute = "value")
    String name();

    /**
     * Whether this DAL is for internal use only.
     *
     * <p>An internal DAL is registered like any other and keeps every channel-agnostic feature that
     * wraps the {@link Dal} bean itself — validation, transactional behavior, property merging and any
     * cross-cutting interception still apply. What it does <em>not</em> get is exposure on any remote
     * boundary: it is not published there and its existence is not revealed.</p>
     *
     * <p>Because there is no remote boundary, concerns that exist only at that boundary do not apply to
     * it: callers of an internal DAL are trusted in-process code.</p>
     *
     * <p>Defaults to {@code false} (the DAL is exposed).</p>
     */
    boolean internal() default false;

    /**
     * The operations this DAL exposes on its remote boundary.
     *
     * <p>This is the per-operation generalization of {@link #internal()}: it selects which operations are
     * published. An operation that is not listed is not exposed — it is left out of the boundary's
     * description, and a call to it is rejected before reaching the {@link Dal}. Defaults to every
     * operation, so a DAL declared without this attribute keeps its full surface.</p>
     *
     * <p>The restriction is a boundary concern only: the {@link Dal} bean keeps every operation, so
     * trusted in-process callers are unaffected. How a boundary documents a non-exposed operation, and
     * how it answers a call to one, are defined where that boundary is implemented.</p>
     *
     * <p>Precedence: {@link #internal()} wins. When {@code internal = true} the DAL has no remote
     * boundary and this attribute is ignored. When {@code internal = false}, the list must not be empty
     * — declare at least one operation, or mark the DAL {@code internal = true} to expose none.</p>
     */
    DalOperationType[] operations() default {
        DalOperationType.CREATE,
        DalOperationType.READ,
        DalOperationType.READ_ONE,
        DalOperationType.UPDATE,
        DalOperationType.DELETE
    };
}
