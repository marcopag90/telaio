package com.paganbit.telaio.security.interceptor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalAdapterContext;
import com.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.exception.DalSortFieldNotReadableException;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.security.adapter.DenyAllDalAuthAdapter;
import com.paganbit.telaio.security.adapter.NoopDalRbacAdapter;
import com.paganbit.telaio.security.adapter.PermitAllDalAuthAdapter;
import com.paganbit.telaio.security.annotation.DalSecurity;
import com.paganbit.telaio.security.exception.DalAccessDeniedException;
import com.paganbit.telaio.security.exception.DefaultDalAccessDeniedMessageResolver;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.json.JsonMapper;

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
        provider = new DalSecurityInterceptorProvider(
            new DefaultDalAccessDeniedMessageResolver(),
            new JsonPropertyPathResolver(JsonMapper.builder().build()));
        // Every produced interceptor carries a field-existence predicate for its DAL's entity.
        Dal<?, ?> dal = mock(Dal.class);
        lenient().doReturn(FieldEntity.class).when(dal).getEntityClass();
        lenient().doReturn(dal).when(dalManager).getServiceByName("myDal");
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

    @Test
    void hiddenExistingField_isRejectedAsDenied() throws Throwable {
        // The field resolves on the entity (under its wire name), so the RBAC rejection stands.
        MethodInterceptor interceptor = distinguishingInterceptor();

        MethodInvocation invocation = readInvocation(sortBy("cost_price"));
        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOf(DalSortFieldNotReadableException.class);
        verify(invocation, never()).proceed();
    }

    @Test
    void hiddenFieldUnderItsJavaSpelling_isRejectedAsDenied() throws Throwable {
        // Both spellings of a renamed property count as existing, matching the read path's resolution.
        MethodInterceptor interceptor = distinguishingInterceptor();

        MethodInvocation invocation = readInvocation(sortBy("costPrice"));
        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOf(DalSortFieldNotReadableException.class);
        verify(invocation, never()).proceed();
    }

    @Test
    void unknownField_fallsThroughToTheRead() throws Throwable {
        // The field does not resolve on the entity: the read proceeds and its own validation rejects it.
        MethodInterceptor interceptor = distinguishingInterceptor();

        MethodInvocation invocation = readInvocation(sortBy("nope"));
        when(invocation.proceed()).thenReturn(new PageImpl<>(java.util.List.of()));
        interceptor.invoke(invocation);
        verify(invocation).proceed();
    }

    /**
     * An interceptor for a DAL over {@link FieldEntity} whose RBAC adapter rejects every field, so the
     * outcome depends purely on the field-existence resolution.
     */
    private MethodInterceptor distinguishingInterceptor() {
        CustomRbacAdapter rbacAdapter = denyAllRbacAdapter();
        CustomAuthAdapter authAdapter = permittingAuthAdapter();
        when(dalManager.getAdapter(CustomRbacAdapter.class)).thenReturn(rbacAdapter);
        when(dalManager.getAdapter(CustomAuthAdapter.class)).thenReturn(authAdapter);
        return requireNonNull(provider.getInterceptor(contextFor(SecuredDal.class)));
    }

    private CustomRbacAdapter denyAllRbacAdapter() {
        CustomRbacAdapter rbacAdapter = mock(CustomRbacAdapter.class);
        when(rbacAdapter.canFilterOn(any(), any())).thenReturn(false);
        return rbacAdapter;
    }

    private CustomAuthAdapter permittingAuthAdapter() {
        CustomAuthAdapter authAdapter = mock(CustomAuthAdapter.class);
        when(authAdapter.authorizeRead(any())).thenReturn(true);
        return authAdapter;
    }

    private MethodInvocation readInvocation(Pageable pageable) throws NoSuchMethodException {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(
            DalOperationAdapter.class.getMethod("read", FilterNode.class, Pageable.class));
        when(invocation.getArguments()).thenReturn(new Object[]{null, pageable});
        return invocation;
    }

    private static Pageable sortBy(String property) {
        return PageRequest.of(0, 10, Sort.by(property));
    }

    private DalAdapterContext contextFor(Class<?> dalBeanClass) {
        return new DalAdapterContext("myDal", dalBeanClass, dalManager);
    }

    private MethodInvocation createInvocation() throws NoSuchMethodException {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(DalOperationAdapter.class.getMethod("create", Map.class));
        when(invocation.getArguments()).thenReturn(new Object[]{Map.of("a", 1)});
        return invocation;
    }

    private CustomAuthAdapter denyingAuthAdapter() {
        CustomAuthAdapter authAdapter = mock(CustomAuthAdapter.class);
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

    /**
     * Fixture entity for the field-existence tests: {@code costPrice} is renamed on the wire.
     */
    static class FieldEntity {
        public String name;
        @JsonProperty("cost_price")
        public java.math.BigDecimal costPrice;
    }
}
