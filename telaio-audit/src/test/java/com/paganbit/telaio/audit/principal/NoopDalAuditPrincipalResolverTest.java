package com.paganbit.telaio.audit.principal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class NoopDalAuditPrincipalResolverTest {

    @Test
    void resolvePrincipal_shouldReturnNull() {
        NoopDalAuditPrincipalResolver resolver = new NoopDalAuditPrincipalResolver();
        assertNull(resolver.resolvePrincipal());
    }
}