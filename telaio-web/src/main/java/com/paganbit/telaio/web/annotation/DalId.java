package com.paganbit.telaio.web.annotation;

import com.paganbit.telaio.web.DalRestApiV1;

import java.lang.annotation.*;

/**
 * Annotation used to resolve and convert an entity ID dynamically based on the DAL context.
 * This annotation replaces the need for {@code @PathVariable("id")} and allows runtime conversion
 * to the proper ID type using the DAL registry.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DalId {

    /**
     * The name of the path variable that contains the ID (default is "id").
     */
    String value() default DalRestApiV1.PATH_VARIABLE_ID;
}
