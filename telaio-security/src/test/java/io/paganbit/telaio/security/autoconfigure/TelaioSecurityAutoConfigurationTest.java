package io.paganbit.telaio.security.autoconfigure;

import io.paganbit.telaio.core.adapter.DalAdapterInterceptorProvider;
import io.paganbit.telaio.security.adapter.DenyAllDalAuthAdapter;
import io.paganbit.telaio.security.adapter.NoopDalRbacAdapter;
import io.paganbit.telaio.security.adapter.PermitAllDalAuthAdapter;
import io.paganbit.telaio.security.exception.DalAccessDeniedMessageResolver;
import io.paganbit.telaio.security.exception.DefaultDalAccessDeniedMessageResolver;
import io.paganbit.telaio.security.interceptor.DalSecurityInterceptorProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the conditional wiring of {@link TelaioSecurityAutoConfiguration}: that the baseline security
 * beans are registered by default, the security interceptor provider is ordered correctly, and every
 * {@code @ConditionalOnMissingBean} defers to an application-supplied bean.
 *
 * <p>Beans are asserted by their {@code @Bean}-method name rather than by type, because
 * {@link PermitAllDalAuthAdapter} extends {@link DenyAllDalAuthAdapter}: both are assignable to
 * {@code DenyAllDalAuthAdapter}, so a type lookup would be ambiguous.
 */
class TelaioSecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TelaioSecurityAutoConfiguration.class));

    @Test
    void byDefault_registersBaselineSecurityBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("denyAllDalAuthAdapter");
            assertThat(context).hasBean("permitAllDalAuthAdapter");
            assertThat(context).hasBean("noopDalRbacAdapter");
            assertThat(context).hasBean("dalSecurityInterceptorProvider");
            assertThat(context).hasSingleBean(DalAccessDeniedMessageResolver.class);
            assertThat(context.getBean(DalAccessDeniedMessageResolver.class))
                .isInstanceOf(DefaultDalAccessDeniedMessageResolver.class);
        });
    }

    @Test
    void registeredInterceptorProvider_isOrderedAtSecurityPrecedence() {
        contextRunner.run(context -> assertThat(
            context.getBean(DalSecurityInterceptorProvider.class).getOrder())
            .isEqualTo(DalAdapterInterceptorProvider.SECURITY_PRECEDENCE));
    }

    @Test
    void customMessageResolver_replacesDefault() {
        DalAccessDeniedMessageResolver custom = mock(DalAccessDeniedMessageResolver.class);
        contextRunner
            .withBean("customResolver", DalAccessDeniedMessageResolver.class, () -> custom)
            .run(context -> {
                assertThat(context).doesNotHaveBean("dalAccessDeniedMessageResolver");
                assertThat(context).doesNotHaveBean(DefaultDalAccessDeniedMessageResolver.class);
                assertThat(context.getBean(DalAccessDeniedMessageResolver.class)).isSameAs(custom);
            });
    }

    @Test
    void customDenyAllAuthAdapter_replacesDefault() {
        DenyAllDalAuthAdapter<Object> custom = new DenyAllDalAuthAdapter<>();
        contextRunner
            .withBean("customDeny", DenyAllDalAuthAdapter.class, () -> custom)
            .run(context -> {
                assertThat(context).doesNotHaveBean("denyAllDalAuthAdapter");
                assertThat(context.getBean("customDeny")).isSameAs(custom);
            });
    }

    @Test
    void customPermitAllAuthAdapter_replacesDefault() {
        PermitAllDalAuthAdapter<Object> custom = new PermitAllDalAuthAdapter<>();
        contextRunner
            .withBean("customPermit", PermitAllDalAuthAdapter.class, () -> custom)
            .run(context -> {
                assertThat(context).doesNotHaveBean("permitAllDalAuthAdapter");
                assertThat(context.getBean(PermitAllDalAuthAdapter.class)).isSameAs(custom);
            });
    }

    @Test
    void customNoopRbacAdapter_replacesDefault() {
        NoopDalRbacAdapter<Object> custom = new NoopDalRbacAdapter<>();
        contextRunner
            .withBean("customNoop", NoopDalRbacAdapter.class, () -> custom)
            .run(context -> {
                assertThat(context).doesNotHaveBean("noopDalRbacAdapter");
                assertThat(context.getBean(NoopDalRbacAdapter.class)).isSameAs(custom);
            });
    }

    @Test
    void customInterceptorProvider_replacesDefault() {
        DalSecurityInterceptorProvider custom =
            new DalSecurityInterceptorProvider(mock(DalAccessDeniedMessageResolver.class));
        contextRunner
            .withBean("customProvider", DalSecurityInterceptorProvider.class, () -> custom)
            .run(context -> {
                assertThat(context).doesNotHaveBean("dalSecurityInterceptorProvider");
                assertThat(context.getBean(DalSecurityInterceptorProvider.class)).isSameAs(custom);
            });
    }
}
