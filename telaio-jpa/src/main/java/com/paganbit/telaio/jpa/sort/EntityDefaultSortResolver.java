package com.paganbit.telaio.jpa.sort;

import jakarta.persistence.metamodel.*;
import org.springframework.data.domain.Sort;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves a stable default {@link Sort} for a JPA entity by inspecting its primary key attributes
 * via the JPA metamodel.
 *
 * <p>Handles all standard JPA identity patterns:
 * <ul>
 *   <li>{@code @Id} — sorts by the single ID field ascending</li>
 *   <li>{@code @EmbeddedId} — sorts by each field of the embeddable, prefixed with the
 *       embedded attribute name (e.g. {@code key.idOne ASC}), ordered alphabetically</li>
 *   <li>{@code @IdClass} — sorts by each mapped ID attribute ascending, ordered alphabetically</li>
 * </ul>
 *
 * <p>Attribute names within composite keys are sorted alphabetically to guarantee deterministic
 * ordering regardless of metamodel traversal order.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public final class EntityDefaultSortResolver {

    private EntityDefaultSortResolver() {
    }

    /**
     * Resolves the default sort for the given entity type.
     *
     * @param <E>        the entity type
     * @param entityType the JPA metamodel entity type
     * @return a {@link Sort} derived from the entity's primary key attributes
     */
    @SuppressWarnings("unchecked")
    public static <E> Sort resolve(EntityType<E> entityType) {
        if (entityType.hasSingleIdAttribute()) {
            Class<Object> idJavaType = (Class<Object>) entityType.getIdType().getJavaType();
            SingularAttribute<? super E, ?> idAttr = entityType.getId(idJavaType);
            if (idAttr.getType().getPersistenceType() == Type.PersistenceType.EMBEDDABLE) {
                EmbeddableType<?> embeddable = (EmbeddableType<?>) idAttr.getType();
                List<Sort.Order> orders = embeddable.getSingularAttributes().stream()
                    .sorted(Comparator.comparing(Attribute::getName))
                    .map(attr -> Sort.Order.asc(idAttr.getName() + "." + attr.getName()))
                    .toList();
                return Sort.by(orders);
            }
            return Sort.by(Sort.Direction.ASC, idAttr.getName());
        }
        List<Sort.Order> orders = entityType.getIdClassAttributes().stream()
            .sorted(Comparator.comparing(Attribute::getName))
            .map(attr -> Sort.Order.asc(attr.getName()))
            .toList();
        return Sort.by(orders);
    }
}
