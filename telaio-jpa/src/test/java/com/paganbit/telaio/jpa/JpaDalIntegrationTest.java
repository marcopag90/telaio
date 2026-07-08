package com.paganbit.telaio.jpa;

import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterStringConverter;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link JpaDal} behavior that requires a real JPA metamodel and repository
 * against H2: {@link JpaDal#defaultSort()} (delegates to {@code EntityDefaultSortResolver}),
 * {@link JpaDal#executeReadOne(Object)} (proves {@code ByIdSpecification} works through JpaDal), and
 * {@link JpaDal#executeRead}. The DAL is hand-built with a real repository + {@link EntityManager}
 * and mocked {@code AbstractDal} collaborators (only null-checked on the paths under test), avoiding
 * the cost of booting a full Telaio application context.
 */
@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = JpaDalIntegrationTest.TestConfig.class)
class JpaDalIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestConfig.TestEntityRepository repository;

    private TestJpaDal dal;

    @BeforeEach
    void setUp() {
        dal = new TestJpaDal(repository, entityManager);
        dal.setObjectMapper(JsonMapper.builder().build());
        dal.setValidatorAdapter(new SpringValidatorAdapter(mock(Validator.class)));
        dal.setPropertyMerger(mock(DalPropertyMerger.class));
        dal.setFilterBuilder(mock(FilterBuilder.class));
        dal.setFilterStringConverter(mock(FilterStringConverter.class));
        dal.setTransactionManager(mock(PlatformTransactionManager.class));
        dal.setTransactionPolicy(mock(DalTransactionPolicy.class));
        dal.afterPropertiesSet();
    }

    @Test
    void defaultSort_resolvesIdBasedSortFromRealMetamodel() {
        Sort sort = dal.defaultSort();

        assertThat(sort.isSorted()).isTrue();
        assertThat(sort.getOrderFor("id")).isNotNull();
    }

    @Test
    void executeReadOne_findsPersistedEntityById() {
        TestEntity entity = new TestEntity();
        entity.setName("alpha");
        TestEntity saved = repository.save(entity);

        Optional<TestEntity> found = dal.executeReadOne(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getName()).isEqualTo("alpha");
    }

    @Test
    void executeReadOne_returnsEmptyForUnknownId() {
        assertThat(dal.executeReadOne(-1L)).isEmpty();
    }

    @Test
    void executeRead_withoutFilter_returnsAllPersistedRows() {
        TestEntity a = new TestEntity();
        a.setName("a");
        TestEntity b = new TestEntity();
        b.setName("b");
        repository.save(a);
        repository.save(b);

        Page<TestEntity> page = dal.executeRead(null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
    }

    static class TestJpaDal extends JpaDal<TestEntity, Long> {

        TestJpaDal(JpaDalRepository<TestEntity, Long> repository, EntityManager entityManager) {
            super(repository, entityManager);
        }
    }

    @Entity
    @Getter
    @Setter
    static class TestEntity {

        @Id
        @GeneratedValue
        private Long id;

        private String name;
    }

    @EnableAutoConfiguration
    @EnableJpaRepositories(considerNestedRepositories = true)
    static class TestConfig {

        interface TestEntityRepository extends JpaDalRepository<TestEntity, Long> {
        }
    }
}
