package com.paganbit.telaio.showcase.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

/**
 * Jackson's customization for the showcase.
 *
 * <p>The {@code jackson-datatype-hibernate7} module is auto-registered with
 * {@link Hibernate7Module.Feature#USE_TRANSIENT_ANNOTATION} on, which makes Jackson honor JPA
 * {@code @Transient} and drop such properties from the JSON. The showcase wants {@code @Transient} to
 * govern <em>persistence only</em> — computed fields like {@code Product.profit} must still be
 * serialized — so this re-registers the module with that feature disabled.</p>
 */
@Configuration
public class JacksonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer hibernateTransientSerializationCustomizer() {
        return builder -> builder.addModule(
            new Hibernate7Module().disable(Hibernate7Module.Feature.USE_TRANSIENT_ANNOTATION));
    }
}
