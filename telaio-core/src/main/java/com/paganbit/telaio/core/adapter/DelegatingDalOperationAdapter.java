package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.exception.DalEntityNotFoundException;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Base {@link DalOperationAdapter} backed by a {@link Dal}, exposing the entity directly to the caller.
 *
 * <p>Every operation delegates to the corresponding {@code Dal} method. A {@link #readOne(Object)} that
 * finds nothing raises a {@link DalEntityNotFoundException}, the same failure the {@code Dal} raises on
 * update and delete, so a missing entity is reported identically on every operation.</p>
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 3.0.0
 */
public class DelegatingDalOperationAdapter<E, I> implements DalOperationAdapter<I, E> {

    private final Dal<E, I> dal;

    public DelegatingDalOperationAdapter(Dal<E, I> dal) {
        this.dal = dal;
    }

    @Override
    public E create(Map<String, Object> input) {
        return dal.create(input);
    }

    @Override
    public Page<E> read(@Nullable FilterNode filter, Pageable pageable) {
        return dal.read(filter, pageable);
    }

    @Override
    public E readOne(I id) {
        return dal.readOne(id).orElseThrow(() -> new DalEntityNotFoundException(dal.getEntityClass(), id));
    }

    @Override
    public Optional<E> update(I id, Map<String, Object> patch) {
        return dal.update(id, patch);
    }

    @Override
    public void delete(I id) {
        dal.delete(id);
    }
}
