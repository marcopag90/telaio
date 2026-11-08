package com.paganbit.telaio.core;

/**
 * Describes the types a DAL (Data Access Layer) manages.
 *
 * <p>Generic type arguments are erased at runtime, so a DAL states its entity and identifier classes
 * explicitly. Framework code relies on them to convert property maps into entities, resolve JSON
 * property names, decode identifiers, and build error messages.</p>
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalMetadata<E, I> {

    /**
     * Returns the class of the managed entity.
     *
     * @return the entity class, never {@code null}
     */
    Class<E> getEntityClass();

    /**
     * Returns the class of the managed entity's identifier.
     *
     * @return the identifier class, never {@code null}
     */
    Class<I> getIdClass();
}
