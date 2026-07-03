package io.paganbit.telaio.jpa.sort;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = EntityDefaultSortResolverTest.TestConfig.class)
class EntityDefaultSortResolverTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void resolve_simpleId() {
        final var entityType = testEntityManager.getEntityManager()
            .getMetamodel()
            .entity(TestConfig.EntityWithSimpleId.class);

        Sort sort = EntityDefaultSortResolver.resolve(entityType);

        List<Sort.Order> orders = sort.toList();
        assertEquals(1, orders.size());
        assertEquals("id", orders.getFirst().getProperty());
        assertEquals(Sort.Direction.ASC, orders.getFirst().getDirection());
    }

    @Test
    void resolve_embeddedId() {
        final var entityType = testEntityManager.getEntityManager()
            .getMetamodel()
            .entity(TestConfig.EntityWithEmbeddedId.class);

        Sort sort = EntityDefaultSortResolver.resolve(entityType);

        List<Sort.Order> orders = sort.toList();
        assertEquals(2, orders.size());
        // sorted alphabetically: idOne before idTwo
        assertEquals("key.idOne", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());
        assertEquals("key.idTwo", orders.get(1).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(1).getDirection());
    }

    @Test
    void resolve_idClass() {
        final var entityType = testEntityManager.getEntityManager()
            .getMetamodel()
            .entity(TestConfig.EntityWithIdClass.class);

        Sort sort = EntityDefaultSortResolver.resolve(entityType);

        List<Sort.Order> orders = sort.toList();
        assertEquals(2, orders.size());
        // sorted alphabetically: idOne before idTwo
        assertEquals("idOne", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());
        assertEquals("idTwo", orders.get(1).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(1).getDirection());
    }

    @EnableAutoConfiguration
    static class TestConfig {

        @Entity
        @Data
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        public static class EntityWithSimpleId {
            @Id
            @EqualsAndHashCode.Include
            private Long id;
        }

        @Entity
        @Data
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        public static class EntityWithEmbeddedId {
            @EmbeddedId
            @EqualsAndHashCode.Include
            private CompositeKey key;
        }

        @Embeddable
        @Data
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        public static class CompositeKey implements Serializable {
            @EqualsAndHashCode.Include
            private Long idOne;
            @EqualsAndHashCode.Include
            private String idTwo;
        }

        @Entity
        @IdClass(EntityWithIdClass.IdClassKey.class)
        @Data
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        public static class EntityWithIdClass {
            @Id
            @EqualsAndHashCode.Include
            private Long idOne;
            @Id
            @EqualsAndHashCode.Include
            private String idTwo;

            public record IdClassKey(Long idOne, String idTwo) implements Serializable {
            }
        }
    }
}
