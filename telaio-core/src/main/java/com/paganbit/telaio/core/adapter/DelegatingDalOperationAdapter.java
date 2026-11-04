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
 * <p>Every operation delegates to the corresponding {@code Dal} method and returns its result as is:
 * the entity, untouched. A {@link #readOne(Object)} that finds nothing raises a
 * {@link DalEntityNotFoundException}, the same failure {@link com.paganbit.telaio.core.AbstractDal}
 * raises on update and delete, so a missing entity is reported identically on every operation.</p>
 *
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 3.0.0
 */
public class DelegatingDalOperationAdapter<I> implements DalOperationAdapter<I> {

    private final Dal<?, I> dal;

    public DelegatingDalOperationAdapter(Dal<?, I> dal) {
        this.dal = dal;
    }

    @Override
    public Object create(Map<String, Object> input) {
        return dal.create(input);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<Object> read(@Nullable FilterNode filter, Pageable pageable) {
        // The page is handed back as is; widening its element type costs no copy.
        return (Page<Object>) dal.read(filter, pageable);
    }

    @Override
    public Object readOne(I id) {
        return dal.readOne(id).orElseThrow(() -> new DalEntityNotFoundException(dal.getEntityClass(), id));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Object> update(I id, Map<String, Object> patch) {
        return (Optional<Object>) dal.update(id, patch);
    }

    @Override
    public void delete(I id) {
        dal.delete(id);
    }
}
