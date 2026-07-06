package com.paganbit.telaio.showcase.dal.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Product entity for the property-based RBAC use case. Two aspects are worth noting:
 * <ul>
 *   <li><b>{@code @JsonProperty} renames</b> ({@code cost_price}, {@code internal_sku}) — the
 *       {@link com.paganbit.telaio.security.adapter.PropertyBasedDalRbacAdapter} keys its field maps by the
 *       <em>Java</em> property name (via {@code propertyName(Product::getCostPrice)}) yet filters the JSON
 *       payload by the <em>serialized</em> name; the adapter translates between the two automatically, so
 *       renames stay invisible to the RBAC configuration.</li>
 *   <li><b>Derived / transient fields</b> — {@link #marginPercentage} is a persistent column but its value
 *       is computed by the DAL hooks (never accepted from the client), and {@link #profit} is a
 *       {@code @Transient} field populated after every read/write. See {@link ProductDalService}.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 10, scale = 2)
    @JsonProperty("cost_price")
    private BigDecimal costPrice;

    /**
     * Profit margin as a percentage. Derived by {@link ProductDalService} from {@code price} and
     * {@code costPrice} on create/update — it is never read from the request payload.
     */
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(precision = 5, scale = 2)
    private BigDecimal marginPercentage;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String sku;

    @Column(unique = true)
    @JsonProperty("internal_sku")
    private String internalSku;

    @Column
    private String category;

    @NotNull
    @Column(nullable = false)
    private Boolean available = true;

    /**
     * Absolute profit ({@code price - costPrice}). Not persisted; computed by {@link ProductDalService}
     * after every read and write so the value travels with the entity in API responses.
     *
     * <p>JPA {@code @Transient} keeps it out of the database. The Jackson Hibernate module would also
     * strip it from the JSON by default ({@code USE_TRANSIENT_ANNOTATION}); the showcase disables that
     * feature in {@code JacksonConfiguration}, so {@code @Transient} governs persistence only and this
     * computed field is still serialized in API responses.
     */
    @Transient
    @JsonProperty("profit")
    private BigDecimal profit;
}
