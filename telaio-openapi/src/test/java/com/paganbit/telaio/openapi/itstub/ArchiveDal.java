package com.paganbit.telaio.openapi.itstub;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.openapi.fixture.Archive;
import com.paganbit.telaio.web.annotation.DalWeb;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Plain (non-JPA) {@link Dal} exposing only its two read operations on REST, so the integration test can
 * prove the document reads {@code @DalWeb} through the REST channel definition. CRUD methods are inert.
 */
@DalService(name = "archives")
@DalWeb(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
public class ArchiveDal implements Dal<Archive, Long> {

    @Override
    public Archive create(Map<String, Object> properties) {
        return new Archive();
    }

    @Override
    public Page<Archive> read(@Nullable FilterNode filter, Pageable pageable) {
        return Page.empty(pageable);
    }

    @Override
    public Optional<Archive> readOne(Long id) {
        return Optional.empty();
    }

    @Override
    public Optional<Archive> update(Long id, Map<String, Object> properties) {
        return Optional.empty();
    }

    @Override
    public void delete(Long id) {
        // no-op
    }

    @Override
    public Class<Archive> getEntityClass() {
        return Archive.class;
    }

    @Override
    public Class<Long> getIdClass() {
        return Long.class;
    }
}
