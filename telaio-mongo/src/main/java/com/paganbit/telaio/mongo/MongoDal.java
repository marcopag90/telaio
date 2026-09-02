package com.paganbit.telaio.mongo;

import com.paganbit.telaio.core.AbstractDal;
import com.paganbit.telaio.mongo.sort.EntityDefaultSortResolver;
import com.paganbit.telaio.mongo.sort.MongoSortPropertyValidator;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MongoDB-based implementation of {@link com.paganbit.telaio.core.Dal Dal}.
 * <p>
 * Provides CRUD execution through {@link MongoDalRepository} and exposes Mongo-specific
 * metadata through {@link MongoDalMetadata}.
 * <p>
 * Like {@link AbstractDal}, this class relies on setter-based injection so that concrete
 * subclasses stay free of boilerplate. In a Spring context a subclass typically needs no
 * constructor at all:
 * <pre>{@code
 * @DalService(name = "products")
 * public class ProductDalService extends MongoDal<Product, String> { }
 * }</pre>
 * The {@link MongoDalRepository} and {@link MongoOperations} are resolved by Spring through the
 * {@link #setRepository(MongoDalRepository) repository} and
 * {@link #setMongoOperations(MongoOperations) mongoOperations} setters. Spring's generic-aware
 * autowiring selects the {@code MongoDalRepository<E, I>} bean matching the concrete type
 * arguments, so multiple repositories for <em>different</em> entity types coexist without
 * ambiguity. The rare case of two beans sharing the exact same {@code <E, I>} is resolved by
 * overriding the relevant setter with a {@link org.springframework.beans.factory.annotation.Qualifier}
 * or by marking one bean {@link org.springframework.context.annotation.Primary @Primary}.
 * <p>
 * The {@link #MongoDal(MongoDalRepository, MongoOperations) two-argument constructor} is retained
 * for non-Spring (unit-test) instantiation and for fully explicit programmatic wiring; when used,
 * the supplied collaborators are kept unless Spring's autowiring later replaces them.
 * <p>
 * The transaction manager is injected by the {@link #TRANSACTION_MANAGER_BEAN_NAME} qualifier,
 * never by plain type, so multiple persistence backends can coexist in one context. {@code String}
 * ids are the recommended identifier type; {@code org.bson.types.ObjectId} ids are supported as
 * well.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 2.0.0
 */
public class MongoDal<E, I> extends AbstractDal<E, I> implements MongoDalMetadata<E, I> {

    /**
     * Name (and qualifier) of the {@link PlatformTransactionManager} bean routed to Mongo-backed
     * DALs. Declare a bean under this name to take over transaction management for every
     * {@code MongoDal} in the context.
     */
    public static final String TRANSACTION_MANAGER_BEAN_NAME = "telaioMongoTransactionManager";

    private static final String RAW_ID_FIELD = "_id";

    /**
     * The repository used for CRUD operations on the entity.
     * This repository is expected to be a Mongo repository that extends {@link MongoDalRepository}.
     * {@code null} only between construction and setter injection; non-null once the bean is fully
     * initialized (see {@link #afterPropertiesSet()}). Access internally via {@link #getRepository()}.
     */
    protected @Nullable MongoDalRepository<E, I> repository;

    /**
     * The template-level Mongo access used for filtered reads and by-id lookups.
     * This is typically injected by Spring and provides access to the MongoDB context.
     * {@code null} only between construction and setter injection.
     */
    protected @Nullable MongoOperations mongoOperations;

    /**
     * The Spring Data mapping metadata for the entity, used to access metadata about the entity.
     * This is obtained from the {@link MongoOperations} mapping context in
     * {@link #afterPropertiesSet()}. Access internally via {@link #getPersistentEntity()}.
     */
    protected @Nullable MongoPersistentEntity<E> persistentEntity;

    /**
     * The query converter used to convert filter nodes into Mongo {@link Query} objects.
     * Allows dynamic filtering of entities based on various criteria.
     * {@code null} only between construction and setter injection.
     */
    protected @Nullable FilterQueryConverter queryConverter;

    /**
     * Validates caller-supplied sort properties against the Spring Data mapping context.
     * Built from the {@link MongoOperations} mapping context in {@link #afterPropertiesSet()}.
     */
    protected @Nullable MongoSortPropertyValidator sortPropertyValidator;

    protected MongoDal() {
    }

    /**
     * Constructor for explicit wiring (unit tests or programmatic {@code @Bean} declarations
     * that prefer to pass collaborators directly).
     *
     * @param repository      the Mongo repository backing this DAL
     * @param mongoOperations the Mongo operations template
     */
    public MongoDal(MongoDalRepository<E, I> repository, MongoOperations mongoOperations) {
        this.repository = repository;
        this.mongoOperations = mongoOperations;
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        Objects.requireNonNull(repository,
            "MongoDalRepository must be set before using the service; "
                + "no MongoDalRepository<" + getEntityClass().getSimpleName() + ", "
                + getIdClass().getSimpleName() + "> bean was injected");
        final MongoOperations ops =
            Objects.requireNonNull(mongoOperations, "MongoOperations must be set before using the service");
        persistentEntity = resolvePersistentEntity(ops);
        sortPropertyValidator = new MongoSortPropertyValidator(ops.getConverter().getMappingContext());
    }

    @SuppressWarnings("unchecked")
    private MongoPersistentEntity<E> resolvePersistentEntity(MongoOperations ops) {
        return (MongoPersistentEntity<E>) ops.getConverter()
            .getMappingContext()
            .getRequiredPersistentEntity(getEntityClass());
    }

    @Autowired
    public void setRepository(MongoDalRepository<E, I> repository) {
        Objects.requireNonNull(repository, "MongoDalRepository must not be null");
        this.repository = repository;
    }

    @Autowired
    public void setMongoOperations(MongoOperations mongoOperations) {
        Objects.requireNonNull(mongoOperations, "MongoOperations must not be null");
        this.mongoOperations = mongoOperations;
    }

    @Autowired
    public void setQueryConverter(FilterQueryConverter queryConverter) {
        Objects.requireNonNull(queryConverter, "FilterQueryConverter must not be null");
        this.queryConverter = queryConverter;
    }

    /**
     * Qualified injection point: Mongo-backed DALs always receive the
     * {@link #TRANSACTION_MANAGER_BEAN_NAME} bean, keeping by-type autowiring unambiguous in
     * multi-backend contexts.
     */
    @Override
    @Autowired
    public void setTransactionManager(
        @Qualifier(TRANSACTION_MANAGER_BEAN_NAME)
        PlatformTransactionManager transactionManager
    ) {
        super.setTransactionManager(transactionManager);
    }

    @Override
    public MongoDalRepository<E, I> getRepository() {
        return Objects.requireNonNull(repository, "MongoDalRepository has not been initialized");
    }

    @Override
    public MongoPersistentEntity<E> getPersistentEntity() {
        return Objects.requireNonNull(persistentEntity, "MongoPersistentEntity has not been initialized");
    }

    private MongoOperations mongoOperations() {
        return Objects.requireNonNull(mongoOperations, "MongoOperations has not been initialized");
    }

    private FilterQueryConverter queryConverter() {
        return Objects.requireNonNull(queryConverter, "FilterQueryConverter has not been initialized");
    }

    @Override
    protected Sort defaultSort() {
        return EntityDefaultSortResolver.resolve(getPersistentEntity());
    }

    @Override
    protected void validateSortProperty(String property) {
        Objects.requireNonNull(sortPropertyValidator, "MongoSortPropertyValidator has not been initialized")
            .validate(property, getEntityClass());
    }

    @Override
    protected E executeCreate(E entity) {
        return getRepository().save(entity);
    }

    @Override
    protected Page<E> executeRead(@Nullable FilterNode filter, Pageable pageable) {
        if (filter == null) {
            return getRepository().findAll(pageable);
        }
        Query query = queryConverter().convert(filter, getEntityClass());
        List<E> content = mongoOperations().find(Query.of(query).with(pageable), getEntityClass());
        return PageableExecutionUtils.getPage(content, pageable,
            () -> mongoOperations().count(Query.of(query).limit(-1).skip(-1), getEntityClass()));
    }

    @Override
    protected Optional<E> executeReadOne(I id) {
        final var byIdQuery = new Query(Criteria.where(RAW_ID_FIELD).is(id));
        final var readOneQuery = combineWithDefaultQuery(byIdQuery);
        return Optional.ofNullable(mongoOperations().findOne(readOneQuery, getEntityClass()));
    }

    @Override
    protected E executeUpdate(E entity) {
        return getRepository().save(entity);
    }

    @Override
    protected void executeDelete(E entity) {
        getRepository().delete(entity);
    }

    /**
     * Combines the provided query with the default {@link #defaultFilter()}.
     * This method ensures that the default filter is applied to by-id lookups. Only the criteria
     * documents are composed — projection, collation and sort of the inputs are not carried over.
     *
     * @param query the query to combine with the default filter
     * @return a combined query that includes the default filter, or the same instance when no
     * default filter applies
     */
    protected Query combineWithDefaultQuery(Query query) {
        final var defaultFilter = defaultFilter();
        if (defaultFilter == null) {
            return query;
        }
        Query defaultQuery = queryConverter().convert(defaultFilter, getEntityClass());
        // $expr sub-documents are only legal at top level or inside a top-level $and.;
        final var andObject = List.of(query.getQueryObject(), defaultQuery.getQueryObject());
        Document combined = new Document("$and", andObject);
        return new BasicQuery(combined);
    }
}
