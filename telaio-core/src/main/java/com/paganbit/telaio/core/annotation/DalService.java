package com.paganbit.telaio.core.annotation;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.registry.DalRegistry;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * Declares a bean as a DAL service, registering it with the {@link DalRegistry} under the given name.
 *
 * <p>The annotation states the DAL's identity only: its name and whether it is available to channels at
 * all. Which operations it exposes on a given channel is declared with that channel module's own
 * annotation, stacked on the same class, and read by the module's
 * {@link com.paganbit.telaio.core.channel.DalChannelDefinition}.</p>
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
     * <p>An internal DAL is registered like any other and keeps every feature that wraps the
     * {@link Dal} bean itself — validation, transactional behavior, property merging and any
     * cross-cutting interception still apply. What it does <em>not</em> get is a place on any channel:
     * no operation adapter is assembled for it, it is not published and its existence is not revealed,
     * whatever a channel module's own annotation says.</p>
     *
     * <p>Because it is on no channel, concerns that exist only there do not apply to it: callers of an
     * internal DAL are trusted in-process code.</p>
     *
     * <p>Defaults to {@code false} (the DAL is available to every channel present).</p>
     */
    boolean internal() default false;
}
