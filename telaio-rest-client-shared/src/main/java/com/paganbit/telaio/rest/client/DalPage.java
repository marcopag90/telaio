package com.paganbit.telaio.rest.client;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * One page of a DAL read, mirroring the stable {@code PagedModel} JSON shape emitted by the
 * server ({@code {"content": [...], "page": {"size", "number", "totalElements", "totalPages"}}}).
 *
 * @param content the page content; never {@code null}, possibly empty
 * @param page    the page metadata
 * @param <T>     the entity type
 * @author Marco Pagan
 * @since 1.1.0
 */
public record DalPage<T>(List<T> content, Metadata page) {

    /**
     * Page metadata of the {@code PagedModel} wire shape.
     *
     * @param size          the requested page size
     * @param number        the zero-based page index
     * @param totalElements the total number of elements across all pages
     * @param totalPages    the total number of pages
     */
    public record Metadata(int size, int number, long totalElements, long totalPages) {
    }

    public DalPage {
        content = List.copyOf(content);
        Objects.requireNonNull(page, "page must not be null");
    }

    /** Whether this page carries any element. */
    public boolean hasContent() {
        return !content.isEmpty();
    }

    /** Whether this is the last page. */
    public boolean isLast() {
        return page.number() >= page.totalPages() - 1;
    }

    /** The page content as a stream. */
    public Stream<T> stream() {
        return content.stream();
    }
}
