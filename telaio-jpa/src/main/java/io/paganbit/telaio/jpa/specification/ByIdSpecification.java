package io.paganbit.telaio.jpa.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Objects;

/**
 * A JPA {@link Specification} that filters entities by their primary key.
 *
 * <p> This specification supports both simple (single column) and composite (multiple columns) primary keys.
 * It builds a {@link Predicate} that can be passed to a JPA query based on the provided {@link EntityType}
 * and the actual identifier instance.
 *
 * @param <E> the entity type
 * @author Marco Pagan
 * @since 1.0.0
 */
public class ByIdSpecification<E> implements Specification<E> {

    private final transient EntityType<E> entityType;
    private final transient Object id;

    public ByIdSpecification(EntityType<E> entityType, Object id) {
        this.entityType = entityType;
        this.id = id;
    }

    @SuppressWarnings("squid:S3011")
    @Override
    public Predicate toPredicate(
        Root<E> root,
        @Nullable CriteriaQuery<?> query,
        CriteriaBuilder criteriaBuilder
    ) {
        if (entityType.hasSingleIdAttribute()) {
            Class<?> idClass = entityType.getIdType().getJavaType();
            SingularAttribute<? super E, ?> idAttribute = entityType.getId(idClass);
            return criteriaBuilder.equal(root.get(idAttribute.getName()), id);
        }
        Class<?> idClass = id.getClass();
        final var attributes = entityType.getIdClassAttributes();
        final var predicates = new ArrayList<Predicate>();
        for (Attribute<? super E, ?> attribute : attributes) {
            String name = attribute.getName();
            Field field = ReflectionUtils.findField(idClass, name);
            Objects.requireNonNull(field, "Field not found id ID class: " + name);
            field.setAccessible(true);
            Object value;
            value = valueFromField(field);
            predicates.add(criteriaBuilder.equal(root.get(name), value));
        }
        return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    }

    protected Object valueFromField(Field field) {
        try {
            return field.get(id);
        } catch (IllegalAccessException e) {
            throw new ByIdSpecificationException("Failed to access field: " + field.getName(), e);
        }
    }

    static class ByIdSpecificationException extends RuntimeException {
        public ByIdSpecificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
