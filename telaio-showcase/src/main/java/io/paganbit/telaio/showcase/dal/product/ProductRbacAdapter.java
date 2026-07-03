package io.paganbit.telaio.showcase.dal.product;

import io.paganbit.telaio.security.adapter.PropertyBasedDalRbacAdapter;
import io.paganbit.telaio.showcase.role.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static io.paganbit.telaio.introspection.PropertyNameResolver.propertyName;

/**
 * Property-based field-level RBAC for {@link Product}. Each role maps to the set of fields it
 * may read or write, keyed by the <em>Java</em> property name via {@code propertyName(Product::getX)}.
 *
 * <p>Two things to note:
 * <ul>
 *   <li>{@code costPrice} and {@code internalSku} carry {@code @JsonProperty} renames on the entity
 *       ({@code cost_price}, {@code internal_sku}). We still reference them by their Java getters here; the
 *       adapter translates to the JSON name when filtering the payload, so the rename is transparent.</li>
 *   <li>The derived {@code marginPercentage} and the transient {@code profit} are <b>readable</b> (for
 *       {@code DEVELOPER}) but never appear in the writable maps — they are computed by
 *       {@link ProductDalService}, so a client must not be able to set them.</li>
 * </ul>
 */
@Component
public class ProductRbacAdapter extends PropertyBasedDalRbacAdapter<Product> {

    @Override
    protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
        return Map.of(
            UserRole.DEVELOPER, Set.of(
                propertyName(Product::getId),
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getCostPrice),
                propertyName(Product::getMarginPercentage),
                propertyName(Product::getProfit),
                propertyName(Product::getSku),
                propertyName(Product::getInternalSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            ),
            UserRole.ADMIN, Set.of(
                propertyName(Product::getId),
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            ),
            UserRole.USER, Set.of(
                propertyName(Product::getId),
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            )
        );
    }

    @Override
    protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
        return Map.of(
            UserRole.DEVELOPER, Set.of(
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getCostPrice),
                propertyName(Product::getSku),
                propertyName(Product::getInternalSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            ),
            UserRole.ADMIN, Set.of(
                propertyName(Product::getName),
                propertyName(Product::getDescription),
                propertyName(Product::getPrice),
                propertyName(Product::getSku),
                propertyName(Product::getCategory),
                propertyName(Product::getAvailable)
            )
        );
    }
}
