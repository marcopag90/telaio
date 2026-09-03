package com.paganbit.telaio.showcase.dal.product;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Demo products, including the internal-only pricing fields the property-based RBAC of
 * {@code ProductDalService} hides from non-power users.
 */
@Component
class ProductSeeder extends AbstractDemoSeeder {

    private final ProductRepository repository;

    ProductSeeder(ProductRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        repository.save(product("Developer Laptop Pro",
            "High-performance laptop for software development with 32GB RAM and dedicated GPU.",
            "1499.99", "950.00", "36.69", "LAP-DEV-001", "INT-LAP-2024-001", "electronics", true));
        repository.save(product("Mechanical Keyboard RGB",
            "Compact 75% mechanical keyboard with customizable RGB lighting and hot-swap switches.",
            "129.99", "55.00", "57.69", "KEY-MECH-001", "INT-KEY-2024-001", "peripherals", true));
        repository.save(product("4K Monitor 27\"",
            "Ultra-sharp 27-inch 4K IPS display with 144Hz refresh rate and USB-C connectivity.",
            "549.00", "310.00", "43.53", "MON-4K-001", "INT-MON-2024-001", "electronics", false));
    }

    @SuppressWarnings("squid:S107")
    private static Product product(
        String name, String description, String price, String costPrice, String marginPercentage,
        String sku, String internalSku, String category, boolean available
    ) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setCostPrice(new BigDecimal(costPrice));
        product.setMarginPercentage(new BigDecimal(marginPercentage));
        product.setSku(sku);
        product.setInternalSku(internalSku);
        product.setCategory(category);
        product.setAvailable(available);
        return product;
    }
}
