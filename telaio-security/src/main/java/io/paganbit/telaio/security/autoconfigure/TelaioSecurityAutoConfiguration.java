package io.paganbit.telaio.security.autoconfigure;

import io.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import io.paganbit.telaio.security.adapter.DenyAllDalAuthAdapter;
import io.paganbit.telaio.security.adapter.NoopDalRbacAdapter;
import io.paganbit.telaio.security.adapter.PermitAllDalAuthAdapter;
import io.paganbit.telaio.security.exception.DalAccessDeniedMessageResolver;
import io.paganbit.telaio.security.exception.DefaultDalAccessDeniedMessageResolver;
import io.paganbit.telaio.security.interceptor.DalSecurityInterceptorProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio Security
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@AutoConfiguration(after = TelaioCoreAutoConfiguration.class)
public class TelaioSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DenyAllDalAuthAdapter<Object> denyAllDalAuthAdapter() {
        return new DenyAllDalAuthAdapter<>();
    }

    @Bean
    @ConditionalOnMissingBean
    PermitAllDalAuthAdapter<Object> permitAllDalAuthAdapter() {
        return new PermitAllDalAuthAdapter<>();
    }

    @Bean
    @ConditionalOnMissingBean
    NoopDalRbacAdapter<Object> noopDalRbacAdapter() {
        return new NoopDalRbacAdapter<>();
    }

    @Bean
    @ConditionalOnMissingBean
    DalAccessDeniedMessageResolver dalAccessDeniedMessageResolver() {
        return new DefaultDalAccessDeniedMessageResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    DalSecurityInterceptorProvider dalSecurityInterceptorProvider(DalAccessDeniedMessageResolver messageResolver) {
        return new DalSecurityInterceptorProvider(messageResolver);
    }
}
