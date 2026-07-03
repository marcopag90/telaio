package io.paganbit.telaio.jpa.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.converter.FilterSpecificationConverterImpl;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.jpa.JpaDalRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Demonstrates the {@code @JsonProperty} filter bug and its fix end-to-end against a real JPA metamodel
 * (H2) with the actual Turkraft autoconfiguration loaded.
 *
 * <p>The {@link Product} entity exposes its {@code costPrice} attribute on the wire as {@code cost_price}.
 * The first test <em>pins the regression</em>: Turkraft's stock converter cannot resolve the JSON name
 * against the metamodel. The remaining tests prove the {@link JsonAwareFilterSpecificationConverter}
 * (registered as the {@code @Primary} bean by {@code TelaioJpaAutoConfiguration}) resolves both the JSON
 * name and the Java name.</p>
 *
 * <p>Uses a plain {@code @SpringBootTest} (not {@code @DataJpaTest}) because the JPA test slice excludes
 * Turkraft's auto-configurations, which this test needs.</p>
 */
@SpringBootTest(
    classes = JsonAwareFilterSpecificationConverterIT.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class JsonAwareFilterSpecificationConverterIT {

    @Autowired
    private TestApp.ProductRepository repository;

    @Autowired
    private FilterStringConverter filterStringConverter;

    /**
     * Turkraft's stock converter, used to reproduce the broken behaviour.
     */
    @Autowired
    private FilterSpecificationConverterImpl rawConverter;

    /**
     * The interface bean — resolves to the {@code @Primary} JSON-aware decorator.
     */
    @Autowired
    private FilterSpecificationConverter primaryConverter;

    @BeforeEach
    void seed() {
        repository.save(new Product("cheap", new BigDecimal("50.00")));
        repository.save(new Product("premium", new BigDecimal("150.00")));
    }

    @Test
    void primaryConverterIsTheJsonAwareDecorator() {
        assertThat(primaryConverter).isInstanceOf(JsonAwareFilterSpecificationConverter.class);
    }

    @Test
    void rawTurkraftConverterCannotResolveJsonPropertyName() {
        FilterNode node = filterStringConverter.convert("cost_price > 100");
        Specification<Product> spec = rawConverter.convert(node);

        // Regression: Turkraft resolves "cost_price" straight against the metamodel, where the
        // attribute is named "costPrice", so building the query fails. Spring's repository layer
        // translates Hibernate's IllegalArgumentException into InvalidDataAccessApiUsageException.
        assertThatThrownBy(() -> repository.findAll(spec))
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasMessageContaining("cost_price");
    }

    @Test
    void jsonAwareConverterResolvesJsonPropertyName() {
        FilterNode node = filterStringConverter.convert("cost_price > 100");
        Specification<Product> spec = primaryConverter.convert(node);

        assertThat(repository.findAll(spec)).extracting(Product::getName).containsExactly("premium");
    }

    @Test
    void jsonAwareConverterStillResolvesJavaPropertyName() {
        // The Java attribute name must keep working — the fix is additive, not a replacement.
        FilterNode node = filterStringConverter.convert("costPrice > 100");
        Specification<Product> spec = primaryConverter.convert(node);

        assertThat(repository.findAll(spec)).extracting(Product::getName).containsExactly("premium");
    }

    @Entity
    @Table(name = "products")
    @Getter
    @Setter
    @NoArgsConstructor
    static class Product {

        @Id
        @GeneratedValue
        private Long id;

        private String name;

        @JsonProperty("cost_price")
        private BigDecimal costPrice;

        Product(String name, BigDecimal costPrice) {
            this.name = name;
            this.costPrice = costPrice;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(considerNestedRepositories = true)
    static class TestApp {

        interface ProductRepository extends JpaDalRepository<Product, Long> {
        }

        /**
         * Turkraft's {@code FilterConversionServiceConfiguration} requires a pre-existing
         * {@link ConversionService} bean (supplied as {@code mvcConversionService} in web apps). This
         * bare non-web test context has none, so we provide one — {@link DefaultFormattingConversionService}
         * is also the {@code ConverterRegistry} Turkraft registers its converter into.
         */
        @Bean
        ConversionService defaultConversionService() {
            return new DefaultFormattingConversionService();
        }
    }
}
