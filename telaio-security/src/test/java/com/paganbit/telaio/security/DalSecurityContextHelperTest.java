package com.paganbit.telaio.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
class DalSecurityContextHelperTest {

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getCurrentAuthentication_shouldReturnAuthentication_whenPresentInContext() {
        Authentication expectedAuth = new TestingAuthenticationToken("user", "pass", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(expectedAuth);

        assertEquals(expectedAuth, DalSecurityContextHelper.getCurrentAuthentication());
    }

    @Test
    void getCurrentAuthentication_shouldReturnNull_whenNoAuthenticationPresent() {
        assertNull(DalSecurityContextHelper.getCurrentAuthentication());
    }

    @Test
    void getCurrentRequestAttributes_shouldReturnAttributes_whenPresentInContext() {
        RequestAttributes mockAttributes = mock(RequestAttributes.class);
        RequestContextHolder.setRequestAttributes(mockAttributes);

        assertEquals(mockAttributes, DalSecurityContextHelper.getCurrentRequestAttributes());
    }

    @Test
    void getCurrentRequestAttributes_shouldReturnNull_whenNoRequestAttributesPresent() {
        assertNull(DalSecurityContextHelper.getCurrentRequestAttributes());
    }
}
