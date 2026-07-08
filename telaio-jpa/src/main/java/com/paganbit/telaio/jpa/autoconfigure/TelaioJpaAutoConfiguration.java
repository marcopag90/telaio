package com.paganbit.telaio.jpa.autoconfigure;

import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.jpa.filter.JsonAwareFilterSpecificationConverter;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.converter.FilterSpecificationConverterImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio JPA.
 *
 * <p>Most JPA DAL setup is performed by user-declared {@code @DalService}-annotated classes; their
 * registration and proxy wiring are driven by the post-processors contributed by
 * {@link TelaioCoreAutoConfiguration}. This autoconfiguration enforces the ordering and classpath guard
 * ({@code @ConditionalOnClass(DataSource.class)}) that must be satisfied before any JPA DAL is processed,
 * and contributes the one JPA-specific bean below.</p>
 *
 * <p>It registers a {@link JsonAwareFilterSpecificationConverter} as the {@link Primary @Primary}
 * {@link FilterSpecificationConverter}, decorating Turkraft's autoconfigured
 * {@link FilterSpecificationConverterImpl}. This lets filter queries reference {@code @JsonProperty} wire
 * names; {@code JpaDal} picks it up automatically through its
 * {@code @Autowired} setter. The decorator is purely additive and overridable — a consuming application
 * may still define its own primary converter.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@AutoConfiguration(after = {TelaioCoreAutoConfiguration.class, FilterSpecificationConverterImpl.class})
@ConditionalOnClass({DataSource.class, FilterSpecificationConverter.class})
public class TelaioJpaAutoConfiguration {

    /**
     * Decorates Turkraft's converter so JSON wire names resolve to their underlying Java properties.
     * Conditional on Turkraft's converter being present, and marked {@link Primary @Primary} so it is
     * the converter injected into {@code JpaDal}.
     *
     * @param delegate     Turkraft's autoconfigured converter to delegate the actual conversion to
     * @param objectMapper provider of the application {@link ObjectMapper} (falls back to a default
     *                     mapper if none is defined), used to introspect {@code @JsonProperty} renames
     * @return the JSON-aware primary converter
     */
    @Bean
    @Primary
    @ConditionalOnBean(FilterSpecificationConverterImpl.class)
    FilterSpecificationConverter jsonAwareFilterSpecificationConverter(
        FilterSpecificationConverterImpl delegate,
        ObjectProvider<ObjectMapper> objectMapper
    ) {
        return new JsonAwareFilterSpecificationConverter(
            delegate,
            objectMapper.getIfAvailable(() -> JsonMapper.builder().build())
        );
    }
}
