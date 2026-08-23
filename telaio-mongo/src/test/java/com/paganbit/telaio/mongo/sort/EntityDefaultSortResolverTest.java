package com.paganbit.telaio.mongo.sort;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EntityDefaultSortResolver} against a bare {@link MongoMappingContext} —
 * no server needed. Mongo has no composite-id concept, so there are only three shapes: implicit
 * {@code id} property, explicit {@code @Id} property, and no id property at all (raw {@code _id}
 * fallback).
 */
class EntityDefaultSortResolverTest {

    @SuppressWarnings("unused")
    static class WithImplicitId {

        @Nullable
        private String id;

        @Nullable
        private String name;
    }

    @SuppressWarnings("unused")
    static class WithExplicitId {

        @Id
        private String code;

        @Nullable private String name;
    }

    @SuppressWarnings("unused")
    static class WithoutId {

        @Nullable private String name;
    }

    private final MongoMappingContext mappingContext = new MongoMappingContext();

    @Test
    void resolve_implicitIdProperty_sortsAscendingByJavaPropertyName() {
        Sort sort = EntityDefaultSortResolver.resolve(
            mappingContext.getRequiredPersistentEntity(WithImplicitId.class));

        assertThat(sort).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void resolve_explicitIdProperty_sortsAscendingByAnnotatedProperty() {
        Sort sort = EntityDefaultSortResolver.resolve(
            mappingContext.getRequiredPersistentEntity(WithExplicitId.class));

        assertThat(sort).isEqualTo(Sort.by(Sort.Direction.ASC, "code"));
    }

    @Test
    void resolve_withoutIdProperty_fallsBackToRawIdField() {
        Sort sort = EntityDefaultSortResolver.resolve(
            mappingContext.getRequiredPersistentEntity(WithoutId.class));

        assertThat(sort).isEqualTo(Sort.by(Sort.Direction.ASC, "_id"));
    }
}
