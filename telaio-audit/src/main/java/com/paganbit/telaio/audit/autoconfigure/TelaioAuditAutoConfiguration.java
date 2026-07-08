package com.paganbit.telaio.audit.autoconfigure;

import com.paganbit.telaio.audit.event.*;
import com.paganbit.telaio.audit.event.format.DalAuditEventFormatter;
import com.paganbit.telaio.audit.event.format.JsonDalAuditEventFormatter;
import com.paganbit.telaio.audit.event.format.LogfmtDalAuditEventFormatter;
import com.paganbit.telaio.audit.interceptor.DalAuditDeniedInterceptorProvider;
import com.paganbit.telaio.audit.interceptor.DalAuditInterceptorProvider;
import com.paganbit.telaio.audit.principal.DalAuditPrincipalResolver;
import com.paganbit.telaio.audit.principal.NoopDalAuditPrincipalResolver;
import com.paganbit.telaio.audit.principal.SecurityContextDalAuditPrincipalResolver;
import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio Audit.
 *
 * <p>Registers the logging event store and the audit interceptor providers. When Spring Security
 * is on the classpath, the security-aware principal resolver and outcome classifier replace the
 * no-op fallbacks, and denied attempts are additionally audited at the operation-adapter
 * boundary.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@AutoConfiguration(after = TelaioCoreAutoConfiguration.class)
@EnableConfigurationProperties(TelaioAuditProperties.class)
public class TelaioAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DalAuditEventFormatter.class)
    DalAuditEventFormatter dalAuditEventFormatter(TelaioAuditProperties properties) {
        TelaioAuditProperties.Logging logging = properties.getLogging();
        boolean includeMdc = logging.isIncludeMdc();
        return switch (logging.getFormat()) {
            case TEXT -> new LogfmtDalAuditEventFormatter(includeMdc);
            case JSON -> new JsonDalAuditEventFormatter(includeMdc);
        };
    }

    @Bean
    @ConditionalOnMissingBean(DalAuditEventStore.class)
    LoggingDalAuditEventStore loggingDalAuditEventStore(
        DalAuditEventFormatter formatter,
        TelaioAuditProperties properties
    ) {
        return new LoggingDalAuditEventStore(formatter, properties.getLogging().getCategory());
    }

    @Bean
    @ConditionalOnMissingBean(DalAuditPrincipalResolver.class)
    NoopDalAuditPrincipalResolver noopDalAuditPrincipalResolver() {
        return new NoopDalAuditPrincipalResolver();
    }

    @Bean
    @ConditionalOnMissingBean(DalAuditOutcomeClassifier.class)
    DefaultDalAuditOutcomeClassifier defaultDalAuditOutcomeClassifier() {
        return new DefaultDalAuditOutcomeClassifier();
    }

    @Bean
    @ConditionalOnMissingBean
    DalAuditInterceptorProvider dalAuditInterceptorProvider(
        DalAuditEventStore store,
        DalAuditPrincipalResolver principalResolver,
        DalAuditOutcomeClassifier outcomeClassifier,
        ObjectProvider<FilterStringConverter> filterStringConverter
    ) {
        return new DalAuditInterceptorProvider(
            store,
            principalResolver,
            outcomeClassifier,
            filterStringConverter.getIfAvailable(),
            Clock.systemUTC()
        );
    }

    /**
     * Security-aware variants, registered before the outer fallbacks when Spring Security is on
     * the classpath. The class-name condition keeps the enclosing autoconfiguration loadable
     * when it is not.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    static class DalAuditSecurityIntegrationConfiguration {

        @Bean
        @ConditionalOnMissingBean(DalAuditPrincipalResolver.class)
        SecurityContextDalAuditPrincipalResolver securityContextDalAuditPrincipalResolver() {
            return new SecurityContextDalAuditPrincipalResolver();
        }

        @Bean
        @ConditionalOnMissingBean(DalAuditOutcomeClassifier.class)
        SecurityDalAuditOutcomeClassifier securityDalAuditOutcomeClassifier() {
            return new SecurityDalAuditOutcomeClassifier();
        }

        @Bean
        @ConditionalOnMissingBean
        DalAuditDeniedInterceptorProvider dalAuditDeniedInterceptorProvider(
            DalAuditEventStore store,
            DalAuditPrincipalResolver principalResolver,
            DalAuditOutcomeClassifier outcomeClassifier,
            ObjectProvider<FilterStringConverter> filterStringConverter
        ) {
            return new DalAuditDeniedInterceptorProvider(
                store,
                principalResolver,
                outcomeClassifier,
                filterStringConverter.getIfAvailable(),
                Clock.systemUTC()
            );
        }
    }
}
