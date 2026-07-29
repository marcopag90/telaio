package com.paganbit.telaio.rest.client;

import java.util.Objects;

/**
 * A single sort order of a {@link DalPageRequest}, rendered on the wire as the repeatable
 * {@code sort=property,direction} query parameter (Spring Data web convention).
 *
 * <p>{@code property} uses the JSON field names of the entity, exactly as they appear in
 * responses.</p>
 *
 * @param property  the JSON field name to sort by
 * @param direction the sort direction
 * @author Marco Pagan
 * @since 1.1.0
 */
public record DalSort(String property, Direction direction) {

    /** Sort direction. */
    public enum Direction {
        ASC, DESC
    }

    public DalSort {
        Objects.requireNonNull(property, "property must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
    }

    /** Ascending order on the given JSON field name. */
    public static DalSort asc(String property) {
        return new DalSort(property, Direction.ASC);
    }

    /** Descending order on the given JSON field name. */
    public static DalSort desc(String property) {
        return new DalSort(property, Direction.DESC);
    }
}
