package com.paganbit.telaio.showcase.dal.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Price-history record for a {@link Product}. This is the <em>related</em> entity that
 * {@link ProductDalService} writes from inside its create/update lifecycle hooks to demonstrate that such
 * a side effect participates in the <b>same transaction</b> as the primary product write: if either write
 * fails, both roll back atomically.
 *
 * <p>It is intentionally not exposed as its own {@code @DalService} — it is an internal companion table,
 * showing that DAL hooks can drive ordinary JPA repositories.
 */
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "product_price_history")
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @NotNull
    @Column(nullable = false)
    private Long productId;

    @NonNull
    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Why this snapshot was taken, e.g. {@code "INITIAL"} on create or {@code "UPDATE"} on update.
     */
    @NonNull
    @NotNull
    @Column(nullable = false)
    private String reason;

    @NonNull
    @NotNull
    @Column(nullable = false)
    private LocalDateTime recordedAt;

}
