package com.paganbit.telaio.rest.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DalPageRequestTest {

    @Test
    void unpagedSendsNoPagingOrSorting() {
        DalPageRequest request = DalPageRequest.unpaged();

        assertThat(request.page()).isNull();
        assertThat(request.size()).isNull();
        assertThat(request.sort()).isEmpty();
    }

    @Test
    void ofSetsPageAndSizeWithoutSorting() {
        DalPageRequest request = DalPageRequest.of(2, 50);

        assertThat(request.page()).isEqualTo(2);
        assertThat(request.size()).isEqualTo(50);
        assertThat(request.sort()).isEmpty();
    }

    @Test
    void withSortKeepsPagingAndAppliesOrders() {
        DalPageRequest request = DalPageRequest.of(1, 10)
            .withSort(DalSort.asc("name"), DalSort.desc("price"));

        assertThat(request.page()).isEqualTo(1);
        assertThat(request.size()).isEqualTo(10);
        assertThat(request.sort()).containsExactly(DalSort.asc("name"), DalSort.desc("price"));
    }

    @Test
    void sortIsDefensivelyCopied() {
        List<DalSort> mutable = new ArrayList<>(List.of(DalSort.asc("name")));
        DalPageRequest request = new DalPageRequest(0, 20, mutable);

        mutable.add(DalSort.desc("price"));

        assertThat(request.sort()).containsExactly(DalSort.asc("name"));
    }

    @Test
    void nullPageAndSizeAreAccepted() {
        DalPageRequest request = new DalPageRequest(null, null, List.of());

        assertThat(request.page()).isNull();
        assertThat(request.size()).isNull();
    }

    @Test
    void negativePageIsRejected() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new DalPageRequest(-1, 20, List.of()))
            .withMessageContaining("page");
    }

    @Test
    void sizeBelowOneIsRejected() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new DalPageRequest(0, 0, List.of()))
            .withMessageContaining("size");
    }
}
