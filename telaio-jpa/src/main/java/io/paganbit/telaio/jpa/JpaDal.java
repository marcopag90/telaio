package io.paganbit.telaio.jpa;

import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.AbstractDal;
import io.paganbit.telaio.jpa.sort.EntityDefaultSortResolver;
import io.paganbit.telaio.jpa.specification.ByIdSpecification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Objects;
import java.util.Optional;

/**
 * JPA-based implementation of {@link io.paganbit.telaio.core.Dal}.
 * <p>
 * Provides CRUD execution through {@link JpaDalRepository} and exposes JPA-specific
 * metadata through {@link JpaDalMetadata}.
 * <p>
 * Like {@link AbstractDal}, this class relies on setter-based injection so that concrete
 * subclasses stay free of boilerplate. In a Spring context a subclass typically needs no
 * constructor at all:
 * <pre>{@code
 * @DalService(name = "products")
 * public class ProductDalService extends JpaDal<Product, Long> { }
 * }</pre>
 * The {@link JpaDalRepository} and {@link EntityManager} are resolved by Spring through the
 * {@link #setRepository(JpaDalRepository) repository} and
 * {@link #setEntityManager(EntityManager) entityManager} setters. Spring's generic-aware
 * autowiring selects the {@code JpaDalRepository<E, I>} bean matching the concrete type
 * arguments, so multiple repositories for <em>different</em> entity types coexist without
 * ambiguity. The rare case of two beans sharing the exact same {@code <E, I>} is resolved by
 * overriding the relevant setter with a {@link org.springframework.beans.factory.annotation.Qualifier}
 * or by marking one bean {@link org.springframework.context.annotation.Primary @Primary}.
 * <p>
 * The {@link #JpaDal(JpaDalRepository, EntityManager) two-argument constructor} is retained for
 * non-Spring (unit-test) instantiation and for fully explicit programmatic wiring; when used,
 * the supplied collaborators are kept unless Spring's autowiring later replaces them.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public class JpaDal<E, I> extends AbstractDal<E, I> implements JpaDalMetadata<E, I> {

    /**
     * The repository used for CRUD operations on the entity.
     * This repository is expected to be a JPA repository that extends {@link JpaDalRepository}.
     * {@code null} only between construction and setter injection; non-null once the bean is fully
     * initialized (see {@link #afterPropertiesSet()}). Access internally via {@link #getRepository()}.
     */
    protected @Nullable JpaDalRepository<E, I> repository;

    /**
     * The JPA EntityManager used for managing entity persistence and queries.
     * This is typically injected by Spring and provides access to the JPA context.
     * {@code null} only between construction and setter injection.
     */
    protected @Nullable EntityManager entityManager;

    /**
     * The JPA EntityType for the entity, used to access metadata about the entity.
     * This is obtained from the EntityManager's metamodel in {@link #afterPropertiesSet()}.
     * Access internally via {@link #getEntityType()}.
     */
    protected @Nullable EntityType<E> entityType;

    /**
     * The specification converter used to convert filter nodes into JPA specifications.
     * Allows dynamic filtering of entities based on various criteria.
     * {@code null} only between construction and setter injection.
     */
    protected @Nullable FilterSpecificationConverter specificationConverter;

    /**
     * Default constructor used by Spring-managed subclasses. The {@link #repository} and
     * {@link #entityManager} are supplied via setter injection and validated in
     * {@link #afterPropertiesSet()}.
     */
    protected JpaDal() {
    }

    /**
     * Constructor for explicit wiring (unit tests or programmatic {@code @Bean} declarations
     * that prefer to pass collaborators directly).
     *
     * @param repository    the JPA repository backing this DAL
     * @param entityManager the JPA entity manager
     */
    public JpaDal(JpaDalRepository<E, I> repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        Objects.requireNonNull(repository,
            "JpaDalRepository must be set before using the service; "
                + "no JpaDalRepository<" + getEntityClass().getSimpleName() + ", "
                + getIdClass().getSimpleName() + "> bean was injected");
        final EntityManager em =
            Objects.requireNonNull(entityManager, "EntityManager must be set before using the service");
        entityType = em.getMetamodel().entity(getEntityClass());
    }

    @Autowired
    public void setRepository(JpaDalRepository<E, I> repository) {
        Objects.requireNonNull(repository, "JpaDalRepository must not be null");
        this.repository = repository;
    }

    @Autowired
    public void setEntityManager(EntityManager entityManager) {
        Objects.requireNonNull(entityManager, "EntityManager must not be null");
        this.entityManager = entityManager;
    }

    @Autowired
    public void setSpecificationConverter(FilterSpecificationConverter specificationConverter) {
        Objects.requireNonNull(specificationConverter, "FilterSpecificationConverter must not be null");
        this.specificationConverter = specificationConverter;
    }

    @Override
    public JpaDalRepository<E, I> getRepository() {
        return Objects.requireNonNull(repository, "JpaDalRepository has not been initialized");
    }

    @Override
    public EntityType<E> getEntityType() {
        return Objects.requireNonNull(entityType, "EntityType has not been initialized");
    }

    private FilterSpecificationConverter specificationConverter() {
        return Objects.requireNonNull(
            specificationConverter, "FilterSpecificationConverter has not been initialized");
    }

    @Override
    protected Sort defaultSort() {
        return EntityDefaultSortResolver.resolve(getEntityType());
    }

    @Override
    protected E executeCreate(E entity) {
        return getRepository().save(entity);
    }

    @Override
    protected Page<E> executeRead(@Nullable FilterNode filter, Pageable pageable) {
        Specification<E> specification = filter != null
            ? specificationConverter().convert(filter)
            : null;
        return specification != null
            ? getRepository().findAll(specification, pageable)
            : getRepository().findAll(pageable);
    }

    @Override
    protected Optional<E> executeReadOne(I id) {
        final var byIdSpecification = new ByIdSpecification<>(getEntityType(), id);
        final var readOneSpecification = combineWithDefaultSpecification(byIdSpecification);
        return getRepository().findOne(readOneSpecification);
    }

    @Override
    protected E executeUpdate(E entity) {
        return getRepository().save(entity);
    }

    @Override
    protected void executeDelete(I id) {
        getRepository().deleteById(id);
    }

    /**
     * Combines the provided specification with the default {@link #defaultFilter()}.
     * This method ensures that the default filter is applied to all queries.
     *
     * @param specification the specification to combine with the default filter
     * @return a combined specification that includes the default filter
     */
    protected Specification<E> combineWithDefaultSpecification(Specification<E> specification) {
        Specification<E> defaultSpecification;
        final var defaultFilter = defaultFilter();
        if (defaultFilter != null) {
            defaultSpecification = specificationConverter().convert(defaultFilter);
            return specification.and(defaultSpecification);
        }
        return specification;
    }
}
