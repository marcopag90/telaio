package com.paganbit.telaio.openapi.generator;

import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.introspection.DefaultSimpleTypePredicate;
import com.paganbit.telaio.openapi.fixture.Product;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FilterParameterDescriber}.
 */
class FilterParameterDescriberTest {

    private final JsonPropertyPathResolver resolver = new JsonPropertyPathResolver(JsonMapper.builder().build());

    private FilterParameterDescriber describer(boolean includeExamples) {
        return new FilterParameterDescriber(resolver, new DefaultSimpleTypePredicate(), includeExamples);
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

    @Test
    void describe_shouldOmitExampleForInterfaceWithOnlyConstants() {
        // An interface has no superclass and only a static field, so nothing is filterable.
        Parameter parameter = describer(true).describe(ConstantsOnly.class);

        assertThat(parameter.getExample()).isNull();
    }

    @Test
    void describe_shouldSkipSyntheticFieldOfInnerClass() {
        // The non-static inner fixture carries a compiler-generated this$0 field, which must be
        // skipped; the example derives only from the real field.
        Parameter parameter = describer(true).describe(InnerFixture.class);

        assertThat(parameter.getExample()).isEqualTo("label ~ '*text*'");
    }

    @Test
    void describe_shouldLetSubclassFieldShadowSuperclassField() {
        // The subclass String field shadows the superclass numeric field of the same name, so the
        // example takes the like-match form, not the numeric one.
        Parameter parameter = describer(true).describe(ShadowingChild.class);

        assertThat(parameter.getExample()).isEqualTo("amount ~ '*text*'");
    }

    @Test
    void describe_shouldExcludeFieldNotSerializedByJackson() {
        // internalOnly has no getter, so it resolves to no JSON name and is not filterable.
        Parameter parameter = describer(true).describe(PartlySerialized.class);

        assertThat(parameter.getExample()).isEqualTo("label ~ '*text*'");
    }

    @Test
    void describe_shouldFallBackToEqualityExampleForNonTextNonNumericField() {
        // A boolean is simple but neither a String nor a number: equality is the fallback.
        Parameter parameter = describer(true).describe(BooleanOnly.class);

        assertThat(parameter.getExample()).isEqualTo("active : 'value'");
    }

    @ParameterizedTest
    @MethodSource("primitiveNumericFixtures")
    void describe_shouldDeriveGreaterThanExampleForPrimitiveNumericField(Class<?> fixture) {
        Parameter parameter = describer(true).describe(fixture);

        assertThat(parameter.getExample()).isEqualTo("quantity > 0");
    }

    static Stream<Arguments> primitiveNumericFixtures() {
        return Stream.of(
            Arguments.of(IntOnly.class),
            Arguments.of(LongOnly.class),
            Arguments.of(DoubleOnly.class),
            Arguments.of(FloatOnly.class),
            Arguments.of(ShortOnly.class),
            Arguments.of(ByteOnly.class)
        );
    }

    @SuppressWarnings("unused")
    static class ComplexOnly {
        private Product nested;

        public Product getNested() {
            return nested;
        }
    }

    @SuppressWarnings("unused")
    interface ConstantsOnly {
        String KIND = "constant";
    }

    /**
     * Non-static inner fixture: its compiler-generated {@code this$0} field is synthetic. The outer
     * reference below is deliberate — without it the compiler omits the {@code this$0} field entirely.
     */
    @SuppressWarnings("unused")
    class InnerFixture {
        private String label;

        public String getLabel() {
            return label;
        }

        private Object outer() {
            return FilterParameterDescriberTest.this;
        }
    }

    @SuppressWarnings("unused")
    static class ShadowingBase {
        private BigDecimal amount;

        public BigDecimal getAmount() {
            return amount;
        }
    }

    @SuppressWarnings("unused")
    static class ShadowingChild extends ShadowingBase {
        // Shadows the superclass field; no getter of its own (a String-returning getAmount() would
        // not be a valid override of the inherited BigDecimal one) — the JSON name still resolves
        // through the inherited getter.
        private String amount;
    }

    @SuppressWarnings("unused")
    static class PartlySerialized {
        private String internalOnly;
        private String label;

        public String getLabel() {
            return label;
        }
    }

    @SuppressWarnings("unused")
    static class BooleanOnly {
        private boolean active;

        public boolean isActive() {
            return active;
        }
    }

    @SuppressWarnings("unused")
    static class IntOnly {
        private int quantity;

        public int getQuantity() {
            return quantity;
        }
    }

    @SuppressWarnings("unused")
    static class LongOnly {
        private long quantity;

        public long getQuantity() {
            return quantity;
        }
    }

    @SuppressWarnings("unused")
    static class DoubleOnly {
        private double quantity;

        public double getQuantity() {
            return quantity;
        }
    }

    @SuppressWarnings("unused")
    static class FloatOnly {
        private float quantity;

        public float getQuantity() {
            return quantity;
        }
    }

    @SuppressWarnings("unused")
    static class ShortOnly {
        private short quantity;

        public short getQuantity() {
            return quantity;
        }
    }

    @SuppressWarnings("unused")
    static class ByteOnly {
        private byte quantity;

        public byte getQuantity() {
            return quantity;
        }
    }
}
