package com.paganbit.telaio.web.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.web.exception.DalResourceNotFoundException;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Web-boundary {@link DalOperationAdapter} backed by a {@link Dal}, exposing the entity directly to
 * the caller.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public class WebDalOperationAdapter<E, I> implements DalOperationAdapter<I, E> {

    private final Dal<E, I> dal;

    public WebDalOperationAdapter(Dal<E, I> dal) {
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
        return dal.readOne(id).orElseThrow(() -> new DalResourceNotFoundException(id));
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
