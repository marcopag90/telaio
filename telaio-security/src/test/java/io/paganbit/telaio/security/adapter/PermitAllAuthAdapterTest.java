package io.paganbit.telaio.security.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PermitAllAuthAdapterTest {

    @Mock
    private Authentication mockAuthentication;

    private PermitAllDalAuthAdapter<Long> adapter;

    @BeforeEach
    void setUp() {
        adapter = new PermitAllDalAuthAdapter<>();
    }

    @Test
    void shouldPermitCreate() {
        assertTrue(adapter.authorizeCreate(mockAuthentication));
    }

    @Test
    void shouldPermitRead() {
        assertTrue(adapter.authorizeRead(mockAuthentication));
    }

    @Test
    void shouldPermitReadOne() {
        assertTrue(adapter.authorizeReadOne(mockAuthentication, 1L));
    }

    @Test
    void shouldPermitUpdate() {
        assertTrue(adapter.authorizeUpdate(mockAuthentication, 1L));
    }

    @Test
    void shouldPermitDelete() {
        assertTrue(adapter.authorizeDelete(mockAuthentication, 1L));
    }
}
