package com.paganbit.telaio.rest.client;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Paging and sorting parameters of a DAL read, rendered as the {@code page}, {@code size} and
 * repeatable {@code sort} query parameters (Spring Data web convention).
 *
 * <p>{@code null} page or size means "do not send the parameter" — the server then applies its
 * own defaults (page {@code 0}, size {@code 20} unless configured otherwise).</p>
 *
 * @param page zero-based page index, or {@code null} for the server default
 * @param size page size, or {@code null} for the server default
 * @param sort sort orders, applied in sequence; never {@code null}, possibly empty
 * @author Marco Pagan
 * @since 1.1.0
 */
public record DalPageRequest(@Nullable Integer page, @Nullable Integer size, List<DalSort> sort) {

    public DalPageRequest {
        if (page != null && page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size != null && size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        sort = List.copyOf(sort);
    }

    /** No explicit paging or sorting: the server applies its defaults. */
    public static DalPageRequest unpaged() {
        return new DalPageRequest(null, null, List.of());
    }

    /** Explicit page index and size, no sorting. */
    public static DalPageRequest of(int page, int size) {
        return new DalPageRequest(page, size, List.of());
    }

    /** A copy of this request with the given sort orders. */
    public DalPageRequest withSort(DalSort... sort) {
        return new DalPageRequest(page, size, List.of(sort));
    }
}
