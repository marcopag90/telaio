package com.paganbit.telaio.web;

import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.web.registry.WebDalOperationAdapterRegistry;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the registered DAL services over REST, delegating each request to the corresponding
 * {@link DalOperationAdapter}.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@RestController
public class DalRestApiV1Controller implements DalRestApiV1 {

    private final WebDalOperationAdapterRegistry adapterRegistry;
    private final FilterStringConverter filterStringConverter;

    DalRestApiV1Controller(
        WebDalOperationAdapterRegistry adapterRegistry,
        FilterStringConverter filterStringConverter
    ) {
        this.adapterRegistry = adapterRegistry;
        this.filterStringConverter = filterStringConverter;
    }

    @Override
    public Object create(String dalName, Map<String, Object> input) {
        return adapter(dalName).create(input);
    }

    @Override
    public Page<Object> read(String dalName, String filter, Pageable pageable) {
        FilterNode filterNode = StringUtils.hasText(filter)
            ? filterStringConverter.convert(filter)
            : null;
        return adapter(dalName).read(filterNode, pageable);
    }

    @Override
    public Object readOne(String dalName, Object id) {
        return adapter(dalName).readOne(id);
    }

    @Override
    public ResponseEntity<Object> update(String dalName, Object id, Map<String, Object> patch) {
        return adapter(dalName).update(id, patch)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Override
    public void delete(String dalName, Object id) {
        adapter(dalName).delete(id);
    }

    @SuppressWarnings("unchecked")
    private <I, E> DalOperationAdapter<I, E> adapter(String dalName) {
        return (DalOperationAdapter<I, E>) adapterRegistry.get(dalName);
    }
}
