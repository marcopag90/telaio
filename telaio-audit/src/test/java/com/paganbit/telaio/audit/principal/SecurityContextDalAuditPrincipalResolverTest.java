package com.paganbit.telaio.audit.principal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityContextDalAuditPrincipalResolverTest {

    private final SecurityContextDalAuditPrincipalResolver resolver = new SecurityContextDalAuditPrincipalResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedContext_shouldResolveUsername() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("admin", "n/a", List.of()));

        assertThat(resolver.resolvePrincipal()).isEqualTo("admin");
    }

    @Test
    void emptyContext_shouldResolveNull() {
        assertThat(resolver.resolvePrincipal()).isNull();
    }
}
