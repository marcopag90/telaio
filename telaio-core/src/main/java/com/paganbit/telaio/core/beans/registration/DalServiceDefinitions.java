package com.paganbit.telaio.core.beans.registration;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;

/**
 * Builds the {@link DalDefinitionEntry} of a {@link DalService}-annotated bean.
 *
 * <p>Shared by the two post-processors that read the annotation, so both see the same identity for a
 * bean without depending on each other's order.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
final class DalServiceDefinitions {

    private DalServiceDefinitions() {
    }

    /**
     * @param definition the annotation found on the bean's user class
     * @param dalClass   the user-declared class of the bean (never a proxy class)
     * @return the entry describing the DAL
     */
    static DalDefinitionEntry entryOf(DalService definition, Class<? extends Dal<?, ?>> dalClass) {
        return new DalDefinitionEntry(definition.name(), dalClass, definition.internal());
    }
}
