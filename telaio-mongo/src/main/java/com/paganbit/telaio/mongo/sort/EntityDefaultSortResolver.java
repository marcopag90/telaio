package com.paganbit.telaio.mongo.sort;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;

/**
 * Resolves the default {@link Sort} for a Mongo entity from Spring Data's mapping metadata:
 * ascending by the id property (Java name, mapped to {@code _id} at execution time), falling back
 * to the raw {@code _id} field. A deterministic sort guarantees stable pagination when the caller
 * provides an unsorted {@code Pageable}.
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public final class EntityDefaultSortResolver {

    private static final String RAW_ID_FIELD = "_id";

    private EntityDefaultSortResolver() {
    }

    /**
     * Returns the default sort for the given entity: ascending by its id property, or by the raw
     * {@code _id} field when the entity declares no id property (Mongo always stores one).
     *
     * @param entity the entity's mapping metadata
     * @return a non-null, deterministic {@link Sort}
     */
    public static Sort resolve(MongoPersistentEntity<?> entity) {
        MongoPersistentProperty idProperty = entity.getIdProperty();
        if (idProperty != null) {
            return Sort.by(Sort.Direction.ASC, idProperty.getName());
        }
        return Sort.by(Sort.Direction.ASC, RAW_ID_FIELD);
    }
}
