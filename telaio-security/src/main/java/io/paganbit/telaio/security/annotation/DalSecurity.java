package io.paganbit.telaio.security.annotation;

import io.paganbit.telaio.security.adapter.DalAuthAdapter;
import io.paganbit.telaio.security.adapter.DalRbacAdapter;
import io.paganbit.telaio.security.adapter.DenyAllDalAuthAdapter;
import io.paganbit.telaio.security.adapter.NoopDalRbacAdapter;

import java.lang.annotation.*;

/**
 * Declares the authorization and RBAC adapters of a DAL service.
 *
 * <p>Applied alongside {@code @DalService}. When omitted, the module defaults apply.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DalSecurity {

    /**
     * The authorization adapter class to be used with this DAL.
     */
    Class<? extends DalAuthAdapter> authAdapterClass() default DenyAllDalAuthAdapter.class;

    /**
     * Optional RBAC adapter class for filtering input and output data.
     * If not defined, no filtering will be applied.
     */
    Class<? extends DalRbacAdapter> rbacAdapterClass() default NoopDalRbacAdapter.class;
}
