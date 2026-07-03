package io.paganbit.telaio.openapi.fixture;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Test entity with a renamed property ({@code cost_price}) and a Bean Validation constraint, used to
 * verify that schema and filter generation honor Jackson annotations.
 */
@SuppressWarnings("unused")
@Getter
@Setter
public class Product {

    private Long id;

    @NotBlank
    private String name;

    private BigDecimal price;

    @JsonProperty("cost_price")
    private BigDecimal costPrice;
}
