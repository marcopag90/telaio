package com.paganbit.telaio.web.channel;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.channel.DalChannelDefinition;
import com.paganbit.telaio.web.annotation.DalWeb;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The {@link DalChannelDefinition} of the REST channel: reads {@link DalWeb} to answer which operations
 * a DAL exposes on {@link DalChannels#REST}.
 *
 * <p>No annotation means every operation; an annotation means the listed ones; an empty list means none,
 * so the DAL is not on REST. The annotation is looked up through the class hierarchy, so a declaration on
 * a base class applies to its subclasses.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public class DalRestChannelDefinition implements DalChannelDefinition {

    private static final Set<DalOperationType> ALL_OPERATIONS =
        Collections.unmodifiableSet(EnumSet.allOf(DalOperationType.class));

    @Override
    public DalChannel channel() {
        return DalChannels.REST;
    }

    @Override
    public Set<DalOperationType> exposedOperations(Class<?> dalClass) {
        DalWeb declaration = AnnotationUtils.findAnnotation(dalClass, DalWeb.class);
        if (declaration == null) {
            return ALL_OPERATIONS;
        }
        if (declaration.operations().length == 0) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(declaration.operations())));
    }
}
