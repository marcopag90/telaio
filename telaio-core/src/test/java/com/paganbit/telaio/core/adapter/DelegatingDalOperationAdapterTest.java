package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.exception.DalEntityNotFoundException;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelegatingDalOperationAdapterTest {

    private record Company(Long id, String name) {
    }

    @Mock
    private Dal<Company, Long> dal;

    private DelegatingDalOperationAdapter<Long> adapter;

    @BeforeEach
    void setUp() {
        adapter = new DelegatingDalOperationAdapter<>(dal);
    }

    @Test
    void create_delegatesToTheDal() {
        Map<String, Object> input = Map.of("name", "Acme");
        Company created = new Company(1L, "Acme");
        when(dal.create(input)).thenReturn(created);

        assertThat(adapter.create(input)).isSameAs(created);
        verify(dal).create(input);
    }

    @Test
    void read_delegatesToTheDalWithTheSameFilterAndPageable() {
        FilterNode filter = mock(FilterNode.class);
        Pageable pageable = PageRequest.of(0, 5);
        Page<Company> page = new PageImpl<>(List.of(new Company(1L, "Acme")), pageable, 1);
        when(dal.read(filter, pageable)).thenReturn(page);

        assertThat(adapter.read(filter, pageable)).isSameAs(page);
        verify(dal).read(filter, pageable);
    }

    @Test
    void read_passesANullFilterThrough() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Company> page = Page.empty(pageable);
        when(dal.read(null, pageable)).thenReturn(page);

        assertThat(adapter.read(null, pageable)).isSameAs(page);
    }

    @Test
    void readOne_returnsTheEntityWhenPresent() {
        Company company = new Company(7L, "Acme");
        when(dal.readOne(7L)).thenReturn(Optional.of(company));

        assertThat(adapter.readOne(7L)).isSameAs(company);
    }

    @Test
    void readOne_throwsEntityNotFoundCarryingTheDalEntityClassWhenEmpty() {
        when(dal.readOne(7L)).thenReturn(Optional.empty());
        when(dal.getEntityClass()).thenReturn(Company.class);
        Long id = 7L;

        assertThatThrownBy(() -> adapter.readOne(id))
            .isInstanceOf(DalEntityNotFoundException.class)
            .hasMessage("Company was not found for id: [7]");
    }

    @Test
    void update_delegatesToTheDal() {
        Map<String, Object> patch = Map.of("name", "Acme Inc.");
        Optional<Company> updated = Optional.of(new Company(1L, "Acme Inc."));
        when(dal.update(1L, patch)).thenReturn(updated);

        assertThat(adapter.update(1L, patch)).isSameAs(updated);
        verify(dal).update(1L, patch);
    }

    @Test
    void delete_delegatesToTheDal() {
        adapter.delete(1L);

        verify(dal).delete(1L);
        verifyNoMoreInteractions(dal);
    }
}
