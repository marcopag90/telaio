package com.paganbit.telaio.jpa.sort;

import com.paganbit.telaio.core.exception.DalInvalidSortException;
import jakarta.persistence.metamodel.*;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Checks that a sort property path addresses a <em>persistent, orderable</em> attribute of the JPA
 * metamodel. A property that is not mapped ({@code @Transient}, computed getter), or that cannot be
 * ordered by, is rejected with a {@link DalInvalidSortException} — a client fault, instead of a
 * failure while the query is built.
 *
 * <p>Paths are walked segment by segment through managed types; intermediate collections are
 * unwrapped to their element type. Ordering through a to-many path multiplies rows and can distort
 * pagination — the same caveat filtered reads carry.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public final class JpaSortPropertyValidator {

    private JpaSortPropertyValidator() {
    }

    /**
     * Validates one sort property path against {@code root}.
     *
     * @param path the dot-notation property path, already translated to Java property names
     * @param root the metamodel type the path is rooted at
     * @throws DalInvalidSortException if a segment does not address a persistent attribute or the
     *                                 resolved attribute cannot be ordered by
     */
    public static void validate(String path, ManagedType<?> root) {
        Iterator<String> segments = Arrays.asList(path.split("\\.")).iterator();
        ManagedType<?> current = root;
        while (segments.hasNext()) {
            String segment = segments.next();
            if (current == null) {
                // A previous segment resolved to a basic attribute: nothing can follow it.
                throw DalInvalidSortException.unknownProperty(segment, path);
            }
            Attribute<?, ?> attribute = attribute(current, segment, path);
            Type<?> type;
            switch (attribute) {
                // PropertyPath has no key/value vocabulary: a Map attribute is never sortable.
                case MapAttribute<?, ?, ?> ignored -> throw DalInvalidSortException.notSortable(path);
                case PluralAttribute<?, ?, ?> plural -> {
                    if (!segments.hasNext()) {
                        // Nothing to order a collection itself by.
                        throw DalInvalidSortException.notSortable(path);
                    }
                    type = plural.getElementType();
                }
                case SingularAttribute<?, ?> singular -> type = singular.getType();
                // Neither singular nor plural: fail closed — nothing Spring Data could order by.
                default -> throw DalInvalidSortException.notSortable(path);
            }
            current = type instanceof ManagedType<?> managed ? managed : null;
        }
    }

    private static Attribute<?, ?> attribute(ManagedType<?> type, String name, String path) {
        try {
            return type.getAttribute(name);
        } catch (IllegalArgumentException unknown) {
            // No subtype fallback: Spring Data's PropertyPath resolves against the root Java type,
            // so a subtype attribute would still fail while the query is built.
            throw DalInvalidSortException.unknownProperty(name, path);
        }
    }
}
