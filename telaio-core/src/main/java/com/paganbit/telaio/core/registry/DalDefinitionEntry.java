package com.paganbit.telaio.core.registry;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.exception.DalDefinitionException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Identifies a registered DAL service.
 *
 * @param name              the unique registration name of the DAL
 * @param dalClass          the concrete {@link Dal} implementation class
 * @param internal          whether the DAL is for internal use only and must not be exposed on any
 *                          remote boundary
 * @param exposedOperations the CRUD operations exposed on the remote boundary; an immutable, possibly
 *                          empty {@link Set}. Ignored when {@code internal} is {@code true}.
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalDefinitionEntry(
    String name,
    Class<? extends Dal<?, ?>> dalClass,
    boolean internal,
    Set<DalOperationType> exposedOperations
) {

    /**
     * Normalizes {@code exposedOperations} into an immutable {@link EnumSet} and validates it: a DAL that
     * is not {@code internal} must expose at least one operation.
     *
     * @throws DalDefinitionException if the DAL is not internal yet exposes no operation
     */
    public DalDefinitionEntry {
        Set<DalOperationType> normalized = (exposedOperations == null || exposedOperations.isEmpty())
            ? EnumSet.noneOf(DalOperationType.class)
            : EnumSet.copyOf(exposedOperations);
        if (!internal && normalized.isEmpty()) {
            throw new DalDefinitionException(
                "DAL '%s' exposes no operation: declare at least one operation in @DalService(operations=...) "
                    .formatted(name) + "or mark it @DalService(internal = true) to expose none");
        }
        exposedOperations = Collections.unmodifiableSet(normalized);
    }

    /**
     * Creates an exposed (non-internal) definition with the full CRUD surface.
     *
     * @param name     the unique registration name of the DAL
     * @param dalClass the concrete {@link Dal} implementation class
     */
    public DalDefinitionEntry(String name, Class<? extends Dal<?, ?>> dalClass) {
        this(name, dalClass, false, EnumSet.allOf(DalOperationType.class));
    }

    /**
     * Creates a definition with the full CRUD surface and the given exposure flag.
     *
     * @param name     the unique registration name of the DAL
     * @param dalClass the concrete {@link Dal} implementation class
     * @param internal whether the DAL is for internal use only
     */
    public DalDefinitionEntry(String name, Class<? extends Dal<?, ?>> dalClass, boolean internal) {
        this(name, dalClass, internal, EnumSet.allOf(DalOperationType.class));
    }
}
