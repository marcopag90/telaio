package com.paganbit.telaio.jpa;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.exception.DalInvalidSortException;
import com.paganbit.telaio.core.json.JsonFieldNameSortRewriter;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.core.transaction.DefaultDalTransactionPolicy;
import com.paganbit.telaio.core.validation.DefaultDalValidator;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterStringConverter;
import jakarta.persistence.*;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DalTransactionPolicy dalTransactionPolicy;

    private TestJpaDal dal;

    @BeforeEach
    void setUp() {
        dal = new TestJpaDal(repository, entityManager);
        final var mapper = JsonMapper.builder().build();
        final var pathResolver = new JsonPropertyPathResolver(mapper);
        dal.setObjectMapper(mapper);
        dal.setSortRewriter(new JsonFieldNameSortRewriter(pathResolver));
        dal.setDalValidator(new DefaultDalValidator(
            new SpringValidatorAdapter(mock(Validator.class)), pathResolver));
        dal.setPropertyMerger(mock(DalPropertyMerger.class));
        dal.setFilterBuilder(mock(FilterBuilder.class));
        dal.setFilterStringConverter(mock(FilterStringConverter.class));
        dal.setTransactionManager(transactionManager);
        dal.setTransactionPolicy(dalTransactionPolicy);
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

    @Test
    void read_withJsonWireNameSort_ordersLikeTheJavaName() {
        persist("beta", "z", 2);
        persist("alpha", "a", 1);

        Page<TestEntity> byWireName = dal.read(null, PageRequest.of(0, 10, Sort.by("display_name")));
        Page<TestEntity> byJavaName = dal.read(null, PageRequest.of(0, 10, Sort.by("label")));

        assertThat(byWireName.getContent()).extracting(TestEntity::getLabel).containsExactly("a", "z");
        assertThat(byJavaName.getContent()).extracting(TestEntity::getLabel).containsExactly("a", "z");
    }

    @Test
    void read_withNestedSortPath_ordersThroughTheEmbeddable() {
        persist("beta", "z", 2);
        persist("alpha", "a", 1);

        Page<TestEntity> page = dal.read(
            null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dims.width")));

        assertThat(page.getContent()).extracting(TestEntity::getName).containsExactly("beta", "alpha");
    }

    @Test
    void read_withUnknownSortProperty_isRejectedAsClientFault() {
        // Was a PropertyReferenceException (a 500 through the web layer) before the sort validation.
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("nope"));

        assertThatThrownBy(() -> dal.read(null, pageable))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("nope");
    }

    @Test
    void read_withSerializedButNotPersistedSortProperty_isRejectedAsClientFault() {
        // `display` is a computed getter: Jackson serializes it, Hibernate does not map it.
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("display"));

        assertThatThrownBy(() -> dal.read(null, pageable))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("display");
    }

    @Test
    void read_withUnsortedPageable_appliesTheDefaultSortWithoutValidation() {
        persist("beta", "z", 2);
        persist("alpha", "a", 1);

        Page<TestEntity> page = dal.read(null, PageRequest.of(0, 10));

        // Default sort is id ascending: insertion order, regardless of names.
        assertThat(page.getContent()).extracting(TestEntity::getName).containsExactly("beta", "alpha");
    }

    private void persist(String name, String label, int width) {
        TestEntity entity = new TestEntity();
        entity.setName(name);
        entity.setLabel(label);
        Dims dims = new Dims();
        dims.setWidth(width);
        entity.setDims(dims);
        repository.save(entity);
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

        @JsonProperty("display_name")
        private String label;

        @Embedded
        private Dims dims;

        /**
         * Serialized by Jackson but not mapped by Hibernate: the sort validation must reject it.
         */
        public String getDisplay() {
            return name + "!";
        }
    }

    @Embeddable
    @Getter
    @Setter
    static class Dims {

        private int width;
    }

    @EnableAutoConfiguration
    @EnableJpaRepositories(considerNestedRepositories = true)
    @Import(DefaultDalTransactionPolicy.class)
    static class TestConfig {

        interface TestEntityRepository extends JpaDalRepository<TestEntity, Long> {
        }
    }
}
