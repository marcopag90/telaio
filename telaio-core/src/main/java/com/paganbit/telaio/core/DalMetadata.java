package com.paganbit.telaio.core;

/**
 * Describes metadata exposed by a DAL (Data Access Layer) component.
 * <p>
 * Implementations provide the runtime Java types of the managed entity and its identifier.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalMetadata<E, I> {

    /**
     * Returns the Java class representing the managed entity type.
     *
     * @return the managed entity class
     */
    Class<E> getEntityClass();

    /**
     * Returns the Java class representing the entity ID type.
     *
     * @return the entity identifier class
     */
    Class<I> getIdClass();
}
