package com.paganbit.telaio.jpa.specification;

import jakarta.persistence.*;
import jakarta.persistence.metamodel.EntityType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.io.Serializable;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ByIdSpecificationTest.TestConfig.class)
class ByIdSpecificationTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void extractValueFromField_throwsException() throws IllegalAccessException {
        final var mockEntityType = mock(EntityType.class);
        final var mockField = mock(Field.class);
        final var byidSpecification = new ByIdSpecification<>(mockEntityType, 1L);
        when(mockField.get(anyLong())).thenThrow(new IllegalAccessException());

        final var thrown = assertThrows(
            ByIdSpecification.ByIdSpecificationException.class,
            () -> byidSpecification.valueFromField(mockField)
        );

        assertTrue(thrown.getMessage().contains("Failed to access field"));
        assertInstanceOf(IllegalAccessException.class, thrown.getCause());
    }

    @Test
    void toPredicate_singleIdAttribute() {
        final var entityClass = TestConfig.EntityWithSingleIdAttribute.class;
        final var entityType = spy(testEntityManager.getEntityManager().getMetamodel().entity(entityClass));
        final var criteriaBuilder = testEntityManager.getEntityManager().getCriteriaBuilder();
        final var criteriaQuery = criteriaBuilder.createQuery(entityClass);
        final var root = criteriaQuery.from(entityClass);
        final var byIdSpecification = new ByIdSpecification<>(entityType, 1L);

        final var predicate = assertDoesNotThrow(
            () -> byIdSpecification.toPredicate(root, criteriaQuery, criteriaBuilder)
        );

        assertNotNull(predicate);
        verify(entityType, times(1)).hasSingleIdAttribute();
        verify(entityType, times(1)).getId(Long.class);
    }

    @Test
    void toPredicate_withSingleEmbeddableId() {
        final var entityClass = TestConfig.EntityWithSingleEmbeddableType.class;
        final var entityType = spy(testEntityManager.getEntityManager().getMetamodel().entity(entityClass));
        final var criteriaBuilder = testEntityManager.getEntityManager().getCriteriaBuilder();
        final var criteriaQuery = criteriaBuilder.createQuery(entityClass);
        final var root = criteriaQuery.from(entityClass);
        final var embeddableId = new TestConfig.EntityWithSingleEmbeddedId();
        embeddableId.setIdOne(1L);
        embeddableId.setIdTwo("2");
        final var byIdSpecification = new ByIdSpecification<>(entityType, embeddableId);

        final var predicate = assertDoesNotThrow(
            () -> byIdSpecification.toPredicate(root, criteriaQuery, criteriaBuilder)
        );

        assertNotNull(predicate);
        verify(entityType, times(1)).hasSingleIdAttribute();
        verify(entityType, times(1)).getId(embeddableId.getClass());
    }

    @Test
    void toPredicate_withPluralAttributes() {
        final var entityClass = TestConfig.EntityWithPluralAttributes.class;
        final var entityType = spy(testEntityManager.getEntityManager().getMetamodel().entity(entityClass));
        final var criteriaBuilder = testEntityManager.getEntityManager().getCriteriaBuilder();
        final var criteriaQuery = criteriaBuilder.createQuery(entityClass);
        final var root = criteriaQuery.from(entityClass);
        final var idAttributes = new TestConfig.EntityWithPluralAttributesId(1L, "2");
        final var byIdSpecification = new ByIdSpecification<>(entityType, idAttributes);

        final var predicate = assertDoesNotThrow(
            () -> byIdSpecification.toPredicate(root, criteriaQuery, criteriaBuilder)
        );

        assertNotNull(predicate);
        verify(entityType, times(1)).hasSingleIdAttribute();
        verify(entityType, times(1)).getIdClassAttributes();
    }

    @EnableAutoConfiguration
    static class TestConfig {

        @Entity
        @Data
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        public static class EntityWithSingleIdAttribute {
            @Id
            @EqualsAndHashCode.Include
            private Long id;
        }

        @Entity
        @Data
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        public static class EntityWithSingleEmbeddableType {
            @EmbeddedId
            @EqualsAndHashCode.Include
            private EntityWithSingleEmbeddedId id;
        }

        @Embeddable
        @Data
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        public static class EntityWithSingleEmbeddedId implements Serializable {
            @EqualsAndHashCode.Include
            private Long idOne;
            @EqualsAndHashCode.Include
            private String idTwo;
        }

        @Entity
        @IdClass(EntityWithPluralAttributesId.class)
        @Data
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        public static class EntityWithPluralAttributes {
            @Id
            @EqualsAndHashCode.Include
            private Long idOne;
            @Id
            @EqualsAndHashCode.Include
            private String idTwo;
        }

        public record EntityWithPluralAttributesId(
            Long idOne,
            String idTwo
        ) implements Serializable {
        }
    }

}