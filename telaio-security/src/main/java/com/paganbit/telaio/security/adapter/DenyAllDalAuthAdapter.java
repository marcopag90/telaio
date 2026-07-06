package com.paganbit.telaio.security.adapter;

import org.springframework.security.core.Authentication;

/**
 * Default secure implementation of {@link DalAuthAdapter} that denies all operations.
 *
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DenyAllDalAuthAdapter<I> implements DalAuthAdapter<I> {

    @Override
    public boolean authorizeCreate(Authentication authentication) {
        return false;
    }

    @Override
    public boolean authorizeRead(Authentication authentication) {
        return false;
    }

    @Override
    public boolean authorizeReadOne(Authentication authentication, I id) {
        return false;
    }

    @Override
    public boolean authorizeUpdate(Authentication authentication, I id) {
        return false;
    }

    @Override
    public boolean authorizeDelete(Authentication authentication, I id) {
        return false;
    }
}
