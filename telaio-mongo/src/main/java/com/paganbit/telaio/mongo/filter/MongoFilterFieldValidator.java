package com.paganbit.telaio.mongo.filter;

import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.paganbit.telaio.core.filter.FilterNodes;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;

import java.util.List;
import java.util.Set;

/**
 * Checks that every field path of a filter addresses a <em>persistent</em> property of the Spring Data
 * mapping context, so that a property the wire exposes but the document does not store (a computed
 * getter) is rejected as a client fault instead of silently matching nothing inside {@code $expr}.
 *
 * <p>Paths are walked segment by segment through embedded documents; collections are unwrapped to their
 * element type. The walk stops at a schemaless property — a {@code Map}, an {@code Object}, or a
 * collection of either — whose remaining segments are dynamic keys and are accepted as long as none
 * looks like an operator; and at a stored reference ({@code @DBRef}/{@code @DocumentReference}), which
 * is never dereferenced and may only be followed by one of the {@code $id}/{@code $ref}/{@code $db}
 * keys. A segment applied to a simple property can never resolve and is rejected.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
final class MongoFilterFieldValidator {

    private static final Set<String> REFERENCE_KEYS = Set.of("$id", "$ref", "$db");

    private final MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;

    MongoFilterFieldValidator(
        MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext
    ) {
        this.mappingContext = mappingContext;
    }

    /**
     * Validates every field reference of {@code filter} against the mapping of {@code entityClass}.
     *
     * @param filter      the filter tree, with field names already translated to Java property names
     * @param entityClass the document class the filter is rooted at
     * @throws DalInvalidFilterException if a path segment does not address a persistent property
     */
    void validate(FilterNode filter, Class<?> entityClass) {
        MongoPersistentEntity<?> root = mappingContext.getRequiredPersistentEntity(entityClass);
        for (FieldNode field : FilterNodes.fieldNodes(filter)) {
            validatePath(field.getName(), root);
        }
    }

    private void validatePath(String path, MongoPersistentEntity<?> root) {
        List<String> segments = List.of(path.split("\\."));
        MongoPersistentEntity<?> current = root;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (current == null) {
                // A previous segment resolved to a simple property: nothing can follow it.
                throw DalInvalidFilterException.unknownField(segment, path);
            }
            MongoPersistentProperty property = current.getPersistentProperty(segment);
            if (property == null) {
                throw DalInvalidFilterException.unknownField(segment, path);
            }
            List<String> tail = segments.subList(i + 1, segments.size());
            if (property.isDbReference() || property.isDocumentReference()) {
                validateReferenceKeys(tail, path);
                return;
            }
            if (isSchemaless(property)) {
                validateDynamicKeys(tail, path);
                return;
            }
            current = property.isEntity() ? mappingContext.getPersistentEntity(property) : null;
        }
    }

    /**
     * A stored reference is never dereferenced: it may stand alone or be followed by exactly one of the
     * raw {@link #REFERENCE_KEYS} of the reference document.
     */
    private static void validateReferenceKeys(List<String> tail, String path) {
        boolean allowed = tail.isEmpty() || (tail.size() == 1 && REFERENCE_KEYS.contains(tail.getFirst()));
        if (!allowed) {
            throw DalInvalidFilterException.unknownField(tail.getFirst(), path);
        }
    }

    /**
     * The segments below a schemaless property are dynamic keys the mapping cannot describe; they are
     * accepted as long as none looks like an operator.
     */
    private static void validateDynamicKeys(List<String> keys, String path) {
        for (String key : keys) {
            if (key.startsWith("$")) {
                throw DalInvalidFilterException.unknownField(key, path);
            }
        }
    }

    /**
     * Whether the property (or the element type of collection property) carries dynamic keys that the
     * mapping context cannot describe: a {@code Map} or a plain {@code Object}.
     */
    private static boolean isSchemaless(MongoPersistentProperty property) {
        TypeInformation<?> element = property.getTypeInformation().getRequiredActualType();
        return property.isMap() || element.isMap() || Object.class.equals(element.getType());
    }
}
