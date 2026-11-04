package com.paganbit.telaio.web.annotation;

import com.paganbit.telaio.core.adapter.DalOperationType;

import java.lang.annotation.*;

/**
 * Declares which CRUD operations a DAL exposes on the REST channel.
 *
 * <p>Stacked on a {@link com.paganbit.telaio.core.annotation.DalService} class, exactly like the audit,
 * metrics and security annotations of their modules. The rule is opt-out:</p>
 * <ul>
 *   <li><b>absent</b> — every operation is exposed, as adding {@code telaio-web} has always meant;</li>
 *   <li><b>present</b> — only the listed operations are exposed; the others are left out of the OpenAPI
 *       document and a request to one of them is rejected before reaching the
 *       {@link com.paganbit.telaio.core.Dal} ({@code 405} with an {@code Allow} header when the URI
 *       still exposes another operation, {@code 404} when it exposes none);</li>
 *   <li><b>{@code operations = {}}</b> — the DAL is not on REST at all: no endpoint, no documentation,
 *       and the same {@code 404} as an unknown name.</li>
 * </ul>
 *
 * <p>{@code @DalService(internal = true)} wins: an internal DAL is on no channel, whatever this
 * annotation says. The restriction is a channel concern only — the {@code Dal} bean keeps every
 * operation for in-process callers.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DalWeb {

    /**
     * The operations exposed on REST. Defaults to every operation; an empty array hides the DAL from
     * REST.
     */
    DalOperationType[] operations() default {
        DalOperationType.CREATE,
        DalOperationType.READ,
        DalOperationType.READ_ONE,
        DalOperationType.UPDATE,
        DalOperationType.DELETE
    };
}
