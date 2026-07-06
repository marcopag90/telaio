package com.paganbit.telaio.openapi.itstub;

import com.turkraft.springfilter.parser.node.FilterNode;
import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.openapi.fixture.Order;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Stub DAL whose entity has a read-only relation and a write-only foreign key, used to verify the served
 * OpenAPI marks them {@code readOnly}/{@code writeOnly}. CRUD methods are inert.
 */
@DalService(name = "orders")
public class OrderDal implements Dal<Order, Long> {

    @Override
    public Order create(Map<String, Object> properties) {
        return new Order();
    }

    @Override
    public Page<Order> read(@Nullable FilterNode filter, Pageable pageable) {
        return Page.empty(pageable);
    }

    @Override
    public Optional<Order> readOne(Long id) {
        return Optional.empty();
    }

    @Override
    public Optional<Order> update(Long id, Map<String, Object> properties) {
        return Optional.empty();
    }

    @Override
    public void delete(Long id) {
        // no-op
    }

    @Override
    public Class<Order> getEntityClass() {
        return Order.class;
    }

    @Override
    public Class<Long> getIdClass() {
        return Long.class;
    }
}
