package com.paganbit.telaio.showcase.dal.product;

import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.jpa.JpaDal;
import com.paganbit.telaio.security.annotation.DalSecurity;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * <h2>Use case — custom authorization, property-based RBAC, lifecycle hooks and a transactional side-effect</h2>
 * <p>
 * The most complete example, exercising the DAL's main extension points together:
 * <ul>
 *   <li><b>Custom CRUD authorization</b> — {@link ProductAuthAdapter} (a handwritten
 *       {@code DalAuthAdapter}) allows reads for everyone but restricts writes to {@code ADMIN}/{@code DEVELOPER}.</li>
 *   <li><b>Property-based field-level RBAC</b> — {@link ProductRbacAdapter} extends
 *       {@code PropertyBasedDalRbacAdapter} and lists readable/writable fields per role; sensitive fields
 *       like {@code cost_price} / {@code internal_sku} are visible only to {@code DEVELOPER}.</li>
 *   <li><b>Derived persistent field</b> — {@code marginPercentage} is recomputed from {@code price} and
 *       {@code costPrice} in {@link #finalizeBeforeCreate}/{@link #finalizeBeforeUpdate}, so the client can
 *       never set it directly (it is also excluded from the writable RBAC maps).</li>
 *   <li><b>Transient calculated field</b> — {@code profit} ({@code price - costPrice}) is populated after
 *       every read and write so each API response carries it without persisting it.</li>
 *   <li><b>Multi-entity transaction</b> — {@link #finalizeAfterCreate}/{@link #finalizeAfterUpdate} write a
 *       {@link ProductPriceHistory} row through a second repository. Because the {@code finalize*} hooks run
 *       <em>inside</em> the operation's transaction (see {@code AbstractDal#create}/{@code #update}), the
 *       history row and the product row commit — or roll back — atomically.</li>
 * </ul>
 * <p>Metrics are left ON (the default); audit is not enabled here to keep the focus on authorization,
 * RBAC, and hooks (see {@code article} for audit).
 */
@DalService(name = "products")
@DalSecurity(
    authAdapterClass = ProductAuthAdapter.class,
    rbacAdapterClass = ProductRbacAdapter.class
)
@RequiredArgsConstructor
public class ProductDalService extends JpaDal<Product, Long> {

    private final ProductPriceHistoryRepository priceHistoryRepository;

    @Override
    protected void finalizeBeforeCreate(Product product) {
        recomputeMargin(product);
    }

    @Override
    protected void finalizeBeforeUpdate(Product product) {
        recomputeMargin(product);
    }

    @Override
    protected void finalizeAfterCreate(Product product) {
        computeProfit(product);
        recordPrice(product, "INITIAL");
    }

    @Override
    protected void finalizeAfterUpdate(Product product) {
        computeProfit(product);
        recordPrice(product, "UPDATE");
    }

    @Override
    protected void finalizeAfterRead(Product product) {
        computeProfit(product);
    }

    @Override
    protected void finalizeAfterReadOne(Product product) {
        computeProfit(product);
    }

    /**
     * Derives the persistent {@code marginPercentage} from price and cost: {@code (price - cost) / price * 100}.
     */
    private void recomputeMargin(Product product) {
        BigDecimal price = product.getPrice();
        BigDecimal cost = product.getCostPrice();
        if (price.signum() > 0) {
            BigDecimal margin = price.subtract(cost)
                .divide(price, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
            product.setMarginPercentage(margin);
        }
    }

    /**
     * Populates the {@code @Transient} {@code profit} field ({@code price - costPrice}).
     */
    private void computeProfit(Product product) {
        BigDecimal price = product.getPrice();
        BigDecimal cost = product.getCostPrice();
        product.setProfit(price.subtract(cost));
    }

    /**
     * Persists a price snapshot in the same transaction as the triggering product write.
     */
    private void recordPrice(Product product, String reason) {
        ProductPriceHistory entry = new ProductPriceHistory(
            product.getId(),
            product.getPrice(),
            reason,
            LocalDateTime.now(ZoneId.systemDefault())
        );
        priceHistoryRepository.save(entry);
    }
}
