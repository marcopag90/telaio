package com.paganbit.telaio.showcase.role;

import org.springframework.security.core.GrantedAuthority;

public enum UserRole implements GrantedAuthority {

    DEVELOPER,
    ADMIN,
    USER;

    @Override
    public String getAuthority() {
        return name();
    }
}
