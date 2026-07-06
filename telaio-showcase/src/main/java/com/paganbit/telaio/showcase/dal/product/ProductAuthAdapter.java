package com.paganbit.telaio.showcase.dal.product;

import com.paganbit.telaio.security.adapter.DalAuthAdapter;
import com.paganbit.telaio.showcase.role.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ProductAuthAdapter implements DalAuthAdapter<Long> {

    @Override
    public boolean authorizeCreate(Authentication authentication) {
        return hasAnyRole(authentication);
    }

    @Override
    public boolean authorizeRead(Authentication authentication) {
        return true;
    }

    @Override
    public boolean authorizeReadOne(Authentication authentication, Long id) {
        return true;
    }

    @Override
    public boolean authorizeUpdate(Authentication authentication, Long id) {
        return hasAnyRole(authentication);
    }

    @Override
    public boolean authorizeDelete(Authentication authentication, Long id) {
        return hasAnyRole(authentication);
    }

    private boolean hasAnyRole(Authentication auth) {
        for (UserRole role : new UserRole[]{UserRole.ADMIN, UserRole.DEVELOPER}) {
            if (auth.getAuthorities().contains(role)) {
                return true;
            }
        }
        return false;
    }
}
