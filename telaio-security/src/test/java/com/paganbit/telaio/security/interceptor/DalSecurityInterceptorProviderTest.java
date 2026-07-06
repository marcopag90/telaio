package com.paganbit.telaio.security.interceptor;

import com.paganbit.telaio.core.adapter.DalAdapterContext;
import com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.security.adapter.DenyAllDalAuthAdapter;
import com.paganbit.telaio.security.adapter.NoopDalRbacAdapter;
import com.paganbit.telaio.security.adapter.PermitAllDalAuthAdapter;
import com.paganbit.telaio.security.annotation.DalSecurity;
import com.paganbit.telaio.security.exception.DalAccessDeniedException;
import com.paganbit.telaio.security.exception.DefaultDalAccessDeniedMessageResolver;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DalSecurityInterceptorProvider}: that it resolves the authorization and RBAC
 * adapter classes from a DAL's {@link DalSecurity} (or the module defaults when absent), looks them up
 * through the {@link DalManager}, and wires them into the produced {@link DalSecurityInterceptor}.
 */
@ExtendWith(MockitoExtension.class)
class DalSecurityInterceptorProviderTest {

    @Mock
    private DalManager dalManager;

    private DalSecurityInterceptorProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DalSecurityInterceptorProvider(new DefaultDalAccessDeniedMessageResolver());
    }

    @Test
    void withoutDalSecurity_resolvesPermitAllAndNoopDefaults() {
        MethodInterceptor interceptor = provider.getInterceptor(contextFor(UnsecuredDal.class));

        assertThat(interceptor).isInstanceOf(DalSecurityInterceptor.class);
        // No @DalSecurity → the DAL is open: PermitAll authorization + Noop (pass-through) RBAC.
        verify(dalManager).getAdapter(PermitAllDalAuthAdapter.class);
        verify(dalManager).getAdapter(NoopDalRbacAdapter.class);
    }

    @Test
    void bareDalSecurity_resolvesDenyAllAndNoopAnnotationDefaults() {
        provider.getInterceptor(contextFor(BareSecuredDal.class));

        // A bare @DalSecurity() is secure-by-default: its authAdapterClass defaults to DenyAll.
        verify(dalManager).getAdapter(DenyAllDalAuthAdapter.class);
        verify(dalManager).getAdapter(NoopDalRbacAdapter.class);
    }

    @Test
    void customDalSecurity_resolvesDeclaredAdapterClasses() {
        provider.getInterceptor(contextFor(SecuredDal.class));

        verify(dalManager).getAdapter(CustomAuthAdapter.class);
        verify(dalManager).getAdapter(CustomRbacAdapter.class);
    }

    @Test
    void dalSecurityOnSuperclass_isFoundOnSubclass() {
        // AnnotationUtils.findAnnotation walks the hierarchy, so an inherited @DalSecurity applies.
        provider.getInterceptor(contextFor(InheritedSecuredDal.class));

        verify(dalManager).getAdapter(CustomAuthAdapter.class);
        verify(dalManager).getAdapter(CustomRbacAdapter.class);
    }

    @Test
    void getOrder_isSecurityPrecedence() {
        assertThat(provider.getOrder()).isEqualTo(DalAdapterInterceptorProvider.SECURITY_PRECEDENCE);
    }

    @Test
    void producedInterceptor_isWiredWithResolvedAuthAdapter() throws Throwable {
        // The auth adapter the DalManager hands back must be the one the interceptor consults: a denied
        // creation must surface as DalAccessDeniedException and never proceed to the DAL.
        CustomAuthAdapter authAdapter = denyingAuthAdapter();
        when(dalManager.getAdapter(CustomAuthAdapter.class)).thenReturn(authAdapter);

        MethodInterceptor interceptor = requireNonNull(provider.getInterceptor(contextFor(SecuredDal.class)));

        MethodInvocation invocation = createInvocation();
        assertThatThrownBy(() -> interceptor.invoke(invocation)).isInstanceOf(DalAccessDeniedException.class);
        verify(authAdapter).authorizeCreate(any());
        verify(invocation, never()).proceed();
    }

    private DalAdapterContext contextFor(Class<?> dalBeanClass) {
        return new DalAdapterContext("myDal", dalBeanClass, dalManager);
    }

    private MethodInvocation createInvocation() throws NoSuchMethodException {
        MethodInvocation invocation = org.mockito.Mockito.mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of("a", 1)});
        return invocation;
    }

    private CustomAuthAdapter denyingAuthAdapter() {
        CustomAuthAdapter authAdapter = org.mockito.Mockito.mock(CustomAuthAdapter.class);
        when(authAdapter.authorizeCreate(any())).thenReturn(false);
        return authAdapter;
    }

    // ------------------------------------------------------------------------
    // Test DALs & adapters
    // ------------------------------------------------------------------------

    static class UnsecuredDal {
    }

    @DalSecurity
    static class BareSecuredDal {
    }

    @DalSecurity(authAdapterClass = CustomAuthAdapter.class, rbacAdapterClass = CustomRbacAdapter.class)
    static class SecuredDal {
    }

    static class InheritedSecuredDal extends SecuredDal {
    }

    static class CustomAuthAdapter extends DenyAllDalAuthAdapter<Object> {
    }

    static class CustomRbacAdapter extends NoopDalRbacAdapter<Object> {
    }
}
