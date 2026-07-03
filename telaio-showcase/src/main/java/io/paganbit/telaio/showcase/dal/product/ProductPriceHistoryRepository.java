package io.paganbit.telaio.showcase.dal.product;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Plain Spring Data repository for {@link ProductPriceHistory}. It is a regular JPA repository (not a
 * {@code JpaDalRepository}) because the price-history table is an internal companion of the product DAL,
 * not an independently exposed resource. {@link ProductDalService} uses it from within its transactional
 * lifecycle hooks.
 */
public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, Long> {
}
