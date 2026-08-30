package com.paganbit.telaio.jpa.filter;

import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.paganbit.telaio.core.filter.FilterNodes;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import jakarta.persistence.metamodel.*;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Checks that every field path of a filter addresses a <em>persistent</em> attribute of the JPA
 * metamodel, so that a property the wire exposes but the persistence unit does not map
 * ({@code @Transient}, computed getter) is rejected as a client fault instead of failing while
 * Hibernate builds the query.
 *
 * <p>Paths are walked segment by segment through managed types (entities and embeddables); plural
 * attributes are unwrapped to their element type, and attributes declared on a subtype of a polymorphic
 * root are accepted the way Hibernate resolves them. A {@code Map} attribute may only be followed by one
 * of the {@code key}/{@code keys}/{@code value}/{@code values} accessors, after which the walk continues on
 * the key or value type. A segment applied to a basic attribute can never resolve and is rejected.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
final class JpaFilterFieldValidator {

    private JpaFilterFieldValidator() {
    }

    /**
     * Validates every field reference of {@code filter} against {@code root}.
     *
     * @param filter the filter tree, with field names already translated to Java property names
     * @param root   the metamodel type the filter is rooted at
     * @throws DalInvalidFilterException if a path segment does not address a persistent attribute
     */
    static void validate(FilterNode filter, ManagedType<?> root) {
        for (FieldNode field : FilterNodes.fieldNodes(filter)) {
            validatePath(field.getName(), root);
        }
    }

    private static void validatePath(String path, ManagedType<?> root) {
        Iterator<String> segments = Arrays.asList(path.split("\\.")).iterator();
        ManagedType<?> current = root;
        while (segments.hasNext()) {
            String segment = segments.next();
            if (current == null) {
                // A previous segment resolved to a basic attribute: nothing can follow it.
                throw DalInvalidFilterException.unknownField(segment, path);
            }
            Attribute<?, ?> attribute = attribute(current, segment);
            if (attribute == null) {
                throw DalInvalidFilterException.unknownField(segment, path);
            }
            Type<?> type;
            switch (attribute) {
                case MapAttribute<?, ?, ?> map -> {
                    if (!segments.hasNext()) {
                        return;
                    }
                    // Map entries are addressed through their key/value accessors, never through a raw key.
                    String accessor = segments.next();
                    type = switch (accessor) {
                        case "key", "keys" -> map.getKeyType();
                        case "value", "values" -> map.getElementType();
                        default -> throw DalInvalidFilterException.unknownField(accessor, path);
                    };
                }
                case PluralAttribute<?, ?, ?> plural -> type = plural.getElementType();
                case SingularAttribute<?, ?> singular -> type = singular.getType();
                // Neither singular nor plural: nothing the metamodel lets a path continue through.
                default -> type = null;
            }
            current = type instanceof ManagedType<?> managed ? managed : null;
        }
    }

    private static @Nullable Attribute<?, ?> attribute(ManagedType<?> type, String name) {
        try {
            return type.getAttribute(name);
        } catch (IllegalArgumentException unknown) {
            // Hibernate also resolves attributes declared on the subtypes of a polymorphic root.
            return type instanceof ManagedDomainType<?> domain ? domain.findSubTypesAttribute(name) : null;
        }
    }
}
