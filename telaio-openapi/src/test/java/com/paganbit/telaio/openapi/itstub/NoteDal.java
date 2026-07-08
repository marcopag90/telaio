package com.paganbit.telaio.openapi.itstub;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.openapi.fixture.Note;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Plain (non-JPA) {@link Dal} keyed by a {@code String} id, registered as a {@code @DalService} for the
 * integration test. CRUD methods are inert.
 */
@DalService(name = "notes")
public class NoteDal implements Dal<Note, String> {

    @Override
    public Note create(Map<String, Object> properties) {
        return new Note();
    }

    @Override
    public Page<Note> read(@Nullable FilterNode filter, Pageable pageable) {
        return Page.empty(pageable);
    }

    @Override
    public Optional<Note> readOne(String id) {
        return Optional.empty();
    }

    @Override
    public Optional<Note> update(String id, Map<String, Object> properties) {
        return Optional.empty();
    }

    @Override
    public void delete(String id) {
        // no-op
    }

    @Override
    public Class<Note> getEntityClass() {
        return Note.class;
    }

    @Override
    public Class<String> getIdClass() {
        return String.class;
    }
}
