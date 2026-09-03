package com.paganbit.telaio.mongo.sort;

import com.paganbit.telaio.core.exception.DalInvalidSortException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;

import java.util.List;
import java.util.Set;

/**
 * Checks that a sort property path addresses a <em>persistent</em> property of the mapping context.
 * An unknown path, or a property the document does not store (a computed getter), is rejected with a
 * {@link DalInvalidSortException} — a client fault, instead of silently ordering on a missing field.
 *
 * <p>Paths are walked segment by segment through embedded documents; collections are unwrapped to
 * their element type. Dynamic keys below a schemaless property are accepted unless they look like an
 * operator; a stored reference is never dereferenced.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public final class MongoSortPropertyValidator {

    private static final Set<String> REFERENCE_KEYS = Set.of("$id", "$ref", "$db");

    private final MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;

    public MongoSortPropertyValidator(
        MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext
    ) {
        this.mappingContext = mappingContext;
    }

    /**
     * Validates one sort property path against the mapping of {@code entityClass}.
     *
     * @param path        the dot-notation property path, already translated to Java property names
     * @param entityClass the document class the path is rooted at
     * @throws DalInvalidSortException if a segment does not address a persistent property
     */
    public void validate(String path, Class<?> entityClass) {
        MongoPersistentEntity<?> root = mappingContext.getRequiredPersistentEntity(entityClass);
        List<String> segments = List.of(path.split("\\."));
        MongoPersistentEntity<?> current = root;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (current == null) {
                // A previous segment resolved to a simple property: nothing can follow it.
                throw DalInvalidSortException.unknownProperty(segment, path);
            }
            MongoPersistentProperty property = current.getPersistentProperty(segment);
            if (property == null) {
                throw DalInvalidSortException.unknownProperty(segment, path);
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
            throw DalInvalidSortException.unknownProperty(tail.getFirst(), path);
        }
    }

    /**
     * The segments below a schemaless property are dynamic keys the mapping cannot describe; they are
     * accepted as long as none looks like an operator.
     */
    private static void validateDynamicKeys(List<String> keys, String path) {
        for (String key : keys) {
            if (key.startsWith("$")) {
                throw DalInvalidSortException.unknownProperty(key, path);
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
