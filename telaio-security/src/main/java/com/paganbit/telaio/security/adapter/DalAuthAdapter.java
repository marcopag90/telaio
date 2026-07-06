package com.paganbit.telaio.security.adapter;

import org.springframework.security.core.Authentication;

/**
 * Contract for operation-level authorization on a DAL resource.
 * <p>
 * Implementations evaluate whether the current authenticated principal is
 * permitted to execute each supported CRUD action. Authorization checks may
 * be global to the resource (for example, create and list reads) or specific
 * to an entity identifier (for example, read one, update, and delete).
 * </p>
 *
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalAuthAdapter<I> {

    /**
     * Determines whether the principal can create a new resource instance.
     *
     * @param authentication the current authentication context
     * @return {@code true} if create is authorized; otherwise {@code false}
     */
    boolean authorizeCreate(Authentication authentication);

    /**
     * Determines whether the principal can perform a read/list operation.
     *
     * @param authentication the current authentication context
     * @return {@code true} if read/list is authorized; otherwise {@code false}
     */
    boolean authorizeRead(Authentication authentication);

    /**
     * Determines whether the principal can read a specific resource instance.
     *
     * @param authentication the current authentication context
     * @param id             the identifier of the target resource
     * @return {@code true} if reading the identified resource is authorized;
     * otherwise {@code false}
     */
    boolean authorizeReadOne(Authentication authentication, I id);

    /**
     * Determines whether the principal can update a specific resource instance.
     *
     * @param authentication the current authentication context
     * @param id             the identifier of the target resource
     * @return {@code true} if updating the identified resource is authorized;
     * otherwise {@code false}
     */
    boolean authorizeUpdate(Authentication authentication, I id);

    /**
     * Determines whether the principal can delete a specific resource instance.
     *
     * @param authentication the current authentication context
     * @param id             the identifier of the target resource
     * @return {@code true} if deleting the identified resource is authorized;
     * otherwise {@code false}
     */
    boolean authorizeDelete(Authentication authentication, I id);
}
