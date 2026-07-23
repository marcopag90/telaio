package com.paganbit.telaio.rest.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DalPageTest {

    private static final DalPage.Metadata FIRST_OF_THREE = new DalPage.Metadata(2, 0, 6, 3);
    private static final DalPage.Metadata LAST_OF_THREE = new DalPage.Metadata(2, 2, 6, 3);

    @Test
    void contentIsDefensivelyCopied() {
        List<String> mutable = new ArrayList<>(List.of("a", "b"));
        DalPage<String> page = new DalPage<>(mutable, LAST_OF_THREE);

        mutable.add("c");

        assertThat(page.content()).containsExactly("a", "b");
    }

    @Test
    void hasContentReflectsEmptiness() {
        assertThat(new DalPage<>(List.of("a"), LAST_OF_THREE).hasContent()).isTrue();
        assertThat(new DalPage<>(List.of(), LAST_OF_THREE).hasContent()).isFalse();
    }

    @Test
    void isLastDistinguishesFinalAndIntermediatePages() {
        assertThat(new DalPage<>(List.of("x"), LAST_OF_THREE).isLast()).isTrue();
        assertThat(new DalPage<>(List.of("x"), FIRST_OF_THREE).isLast()).isFalse();
    }

    @Test
    void streamExposesTheContent() {
        DalPage<String> page = new DalPage<>(List.of("a", "b"), LAST_OF_THREE);

        assertThat(page.stream().toList()).containsExactly("a", "b");
    }

    @Test
    void nullContentIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new DalPage<>(null, LAST_OF_THREE));
    }

    @Test
    void nullPageMetadataIsRejected() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DalPage<>(List.of("a"), null))
            .withMessageContaining("page");
    }
}
