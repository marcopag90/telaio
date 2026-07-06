package com.paganbit.telaio.security.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class DenyAllDalAuthAdapterTest {

    @Mock
    private Authentication mockAuthentication;

    private DenyAllDalAuthAdapter<Long> adapter;

    @BeforeEach
    void setUp() {
        adapter = new DenyAllDalAuthAdapter<>();
    }

    @Test
    void shouldDenyCreate() {
        assertFalse(adapter.authorizeCreate(mockAuthentication));
    }

    @Test
    void shouldDenyRead() {
        assertFalse(adapter.authorizeRead(mockAuthentication));
    }

    @Test
    void shouldDenyReadOne() {
        assertFalse(adapter.authorizeReadOne(mockAuthentication, 1L));
    }

    @Test
    void shouldDenyUpdate() {
        assertFalse(adapter.authorizeUpdate(mockAuthentication, 1L));
    }

    @Test
    void shouldDenyDelete() {
        assertFalse(adapter.authorizeDelete(mockAuthentication, 1L));
    }
}
