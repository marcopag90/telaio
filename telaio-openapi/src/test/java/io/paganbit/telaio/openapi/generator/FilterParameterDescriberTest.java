package io.paganbit.telaio.openapi.generator;

import io.paganbit.telaio.core.json.JsonPropertyPathResolver;
import io.paganbit.telaio.openapi.fixture.Product;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FilterParameterDescriber}.
 */
class FilterParameterDescriberTest {

    private final JsonPropertyPathResolver resolver = new JsonPropertyPathResolver(JsonMapper.builder().build());

    private FilterParameterDescriber describer(boolean includeExamples) {
        return new FilterParameterDescriber(resolver, includeExamples);
    }

    @Test
    void describe_shouldBuildQueryParameter() {
        Parameter parameter = describer(false).describe(Product.class);

        assertThat(parameter.getName()).isEqualTo("q");
        assertThat(parameter.getIn()).isEqualTo("query");
        assertThat(parameter.getRequired()).isFalse();
        assertThat(parameter.getSchema()).isInstanceOf(StringSchema.class);
    }

    @Test
    void describe_shouldUseTerseDescription() {
        String description = describer(false).describe(Product.class).getDescription();

        // A one-line pointer to the language — no operator list, no per-field enumeration.
        assertThat(description).contains("Turkraft Spring Filter")
            .doesNotContain("Supported operators", "Filterable fields")
            .doesNotContain("cost_price", "name");
    }

    @Test
    void describe_shouldDeriveLikeExampleFromFirstStringField() {
        Parameter parameter = describer(true).describe(Product.class);

        assertThat(parameter.getExample()).isEqualTo("name ~ '*text*'");
    }

    @Test
    void describe_shouldDeriveGreaterThanExampleWhenNoStringField() {
        Parameter parameter = describer(true).describe(NumericOnly.class);

        assertThat(parameter.getExample()).isEqualTo("id > 0");
    }

    @Test
    void describe_shouldOmitExampleWhenExamplesDisabled() {
        Parameter parameter = describer(false).describe(Product.class);

        assertThat(parameter.getExample()).isNull();
    }

    @Test
    void describe_shouldOmitExampleWhenNoSimpleFields() {
        Parameter parameter = describer(true).describe(ComplexOnly.class);

        assertThat(parameter.getExample()).isNull();
    }

    @SuppressWarnings("unused")
    static class NumericOnly {
        private Long id;
        private BigDecimal amount;

        public Long getId() {
            return id;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    @SuppressWarnings("unused")
    static class ComplexOnly {
        private Product nested;

        public Product getNested() {
            return nested;
        }
    }
}
