package com.paganbit.telaio.openapi.itstub;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.openapi.fixture.Product;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Plain (non-JPA) {@link Dal} implementation registered as a {@code @DalService} so the integration test
 * exercises the real core registration path without a database. CRUD methods are inert.
 */
@DalService(name = "products")
public class ProductDal implements Dal<Product, Long> {

    @Override
    public Product create(Map<String, Object> properties) {
        return new Product();
    }

    @Override
    public Page<Product> read(@Nullable FilterNode filter, Pageable pageable) {
        return Page.empty(pageable);
    }

    @Override
    public Optional<Product> readOne(Long id) {
        return Optional.empty();
    }

    @Override
    public Optional<Product> update(Long id, Map<String, Object> properties) {
        return Optional.empty();
    }

    @Override
    public void delete(Long id) {
        // no-op
    }

    @Override
    public Class<Product> getEntityClass() {
        return Product.class;
    }

    @Override
    public Class<Long> getIdClass() {
        return Long.class;
    }
}
