package io.paganbit.telaio.security.adapter;

import org.springframework.security.core.Authentication;

/**
 * Authorization adapter that permits all operations unconditionally.
 *
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public class PermitAllDalAuthAdapter<I> extends DenyAllDalAuthAdapter<I> {

    @Override
    public boolean authorizeCreate(Authentication authentication) {
        return true;
    }

    @Override
    public boolean authorizeRead(Authentication authentication) {
        return true;
    }

    @Override
    public boolean authorizeReadOne(Authentication authentication, I id) {
        return true;
    }

    @Override
    public boolean authorizeUpdate(Authentication authentication, I id) {
        return true;
    }

    @Override
    public boolean authorizeDelete(Authentication authentication, I id) {
        return true;
    }
}
