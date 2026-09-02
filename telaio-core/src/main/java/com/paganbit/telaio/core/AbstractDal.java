package com.paganbit.telaio.core;

import com.paganbit.telaio.core.beans.DalPropertyMerger;
import com.paganbit.telaio.core.exception.DalEntityNotFoundException;
import com.paganbit.telaio.core.exception.DalEntityValidationException;
import com.paganbit.telaio.core.exception.DalInvalidSortException;
import com.paganbit.telaio.core.json.JsonFieldNameSortRewriter;
import com.paganbit.telaio.core.transaction.DalTransactionPolicy;
import com.paganbit.telaio.core.validation.DalValidator;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract base class for {@link Dal}, providing shared logic
 * for map-to-entity conversion, validation, and transactional customization hooks.
 *
 * @param <E> the entity type
 * @param <I> the entity identifier type
 * @author Marco Pagan
 * @since 1.0.0
 */
public abstract class AbstractDal<E, I> implements Dal<E, I>, InitializingBean {

    //---------------------------------------------------------------------
    // Metadata
    //---------------------------------------------------------------------

    /**
     * The class of the entity type handled by this service.
     */
    protected final Class<E> entityClass;

    /**
     * The class of the ID type handled by this service.
     */
    protected final Class<I> idClass;

    //---------------------------------------------------------------------
    // Conversion and Validation
    //---------------------------------------------------------------------

    /**
     * Jackson's {@link ObjectMapper} for converting between maps and entities.
     */
    protected ObjectMapper objectMapper;

    /**
     * Validates entities before they are persisted.
     */
    protected DalValidator dalValidator;

    /**
     * Merges properties from a map into an existing entity instance.
     */
    protected DalPropertyMerger propertyMerger;

    /**
     * Translates sort properties from their JSON wire names to Java property names
     * and rejects properties the entity does not expose.
     */
    protected JsonFieldNameSortRewriter sortRewriter;

    //---------------------------------------------------------------------
    // Filter Management
    //---------------------------------------------------------------------

    /**
     * Handles query filter transformations.
     */
    protected FilterBuilder filterBuilder;

    /**
     * Converts string-based filters into structured filter objects.
     */
    protected FilterStringConverter filterStringConverter;

    //---------------------------------------------------------------------
    // Transaction Management
    //---------------------------------------------------------------------

    /**
     * Spring's {@link PlatformTransactionManager} for managing transactions.
     * This is used to execute operations within a transactional context.
     */
    protected PlatformTransactionManager transactionManager;

    /**
     * Policy for managing transactions.
     * This defines how transactions are started, committed, or rolled back.
     */
    protected DalTransactionPolicy transactionPolicy;

    protected AbstractDal() {
        this.entityClass = resolveEntityClass();
        this.idClass = resolveIdClass();
    }

    @Override
    public void afterPropertiesSet() {
        /*
         * This is called by Spring after all dependencies have been injected.
         * It ensures that the service is properly configured before any operations are performed.
         * Setter-based injection is used to avoid bloated constructors and to keep the class more readable.
         * Since concrete subclasses are expected to be Spring-managed beans, dependencies will be injected automatically.
         * This approach also simplifies testing by allowing dependencies to be easily mocked or overridden.
         */
        Objects.requireNonNull(objectMapper, "ObjectMapper must be set before using the service");
        Objects.requireNonNull(propertyMerger, "DalPropertyMerger must be set before using the service");
        Objects.requireNonNull(dalValidator, "DalValidator must be set before using the service");
        Objects.requireNonNull(filterBuilder, "FilterBuilder must be set before using the service");
        Objects.requireNonNull(filterStringConverter, "FilterStringConverter must be set before using the service");
        Objects.requireNonNull(transactionManager, "PlatformTransactionManager must be set before using the service");
        Objects.requireNonNull(transactionPolicy, "DalTransactionPolicy must be set before using the service");
        Objects.requireNonNull(sortRewriter, "JsonFieldNameSortRewriter must be set before using the service");
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setSortRewriter(JsonFieldNameSortRewriter sortRewriter) {
        Objects.requireNonNull(sortRewriter, "JsonFieldNameSortRewriter must not be null");
        this.sortRewriter = sortRewriter;
    }

    public DalValidator getDalValidator() {
        return dalValidator;
    }

    @Autowired
    public void setDalValidator(DalValidator dalValidator) {
        Objects.requireNonNull(dalValidator, "DalValidator must not be null");
        this.dalValidator = dalValidator;
    }

    public DalPropertyMerger getPropertyMerger() {
        return propertyMerger;
    }

    @Autowired
    public void setPropertyMerger(DalPropertyMerger propertyMerger) {
        this.propertyMerger = propertyMerger;
    }

    public FilterBuilder getFilterBuilder() {
        return filterBuilder;
    }

    @Autowired
    public void setFilterBuilder(FilterBuilder filterBuilder) {
        Objects.requireNonNull(filterBuilder, "FilterBuilder must not be null");
        this.filterBuilder = filterBuilder;
    }

    public FilterStringConverter getFilterStringConverter() {
        return filterStringConverter;
    }

    @Autowired
    public void setFilterStringConverter(FilterStringConverter filterStringConverter) {
        Objects.requireNonNull(filterStringConverter, "FilterStringConverter must not be null");
        this.filterStringConverter = filterStringConverter;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        Objects.requireNonNull(transactionManager, "PlatformTransactionManager must not be null");
        this.transactionManager = transactionManager;
    }

    public DalTransactionPolicy getTransactionPolicy() {
        return transactionPolicy;
    }

    @Autowired
    public void setTransactionPolicy(DalTransactionPolicy transactionPolicy) {
        Objects.requireNonNull(transactionPolicy, "DalTransactionPolicy must not be null");
        this.transactionPolicy = transactionPolicy;
    }

    @SuppressWarnings("unchecked")
    private Class<E> resolveEntityClass() {
        Class<?> clazz = Objects.requireNonNull(
            GenericTypeResolver.resolveTypeArguments(getClass(), AbstractDal.class),
            "Unable to resolve entity type"
        )[0];
        return (Class<E>) clazz;
    }

    @SuppressWarnings("unchecked")
    private Class<I> resolveIdClass() {
        Class<?> clazz = Objects.requireNonNull(
            GenericTypeResolver.resolveTypeArguments(getClass(), AbstractDal.class),
            "Unable to resolve id type"
        )[1];
        return (Class<I>) clazz;
    }

    /**
     * Returns the runtime entity class resolved from generic type arguments.
     *
     * @return the managed entity class
     */
    @Override
    public Class<E> getEntityClass() {
        return entityClass;
    }

    /**
     * Returns the runtime identifier class resolved from generic type arguments.
     *
     * @return the managed identifier class
     */
    @Override
    public Class<I> getIdClass() {
        return idClass;
    }

    /**
     * Validates the entity as an instance of {@link #getEntityClass()}.
     *
     * @param target the entity to validate
     * @throws DalEntityValidationException if validation fails
     */
    public void validate(E target) throws DalEntityValidationException {
        this.dalValidator.validate(target, entityClass);
    }

    /**
     * Provides a default filter condition.
     *
     * <p>
     * This method can be overridden by subclasses to define entity-level filter conditions.
     * The default implementation returns {@code null}, meaning no additional filtering is applied.
     * </p>
     *
     * <p>The default filter constrains <strong>every operation</strong>: list reads (AND-combined
     * with the caller's own filter, see {@link #combineWithDefaultFilter(FilterNode)}), by-id
     * reads (via {@link #executeReadOne(Object)}), and therefore also {@link #update(Object, Map)}
     * and {@link #delete(Object)}, which load the entity through the filtered by-id lookup — a
     * hidden entity behaves exactly like a missing one.</p>
     *
     * @return a {@link FilterNode} representing the filter condition,
     * or {@code null} if no default filter applies.
     */
    protected @Nullable FilterNode defaultFilter() {
        return null;
    }

    /**
     * Returns the default {@link Sort} to apply when the caller provides an unsorted {@link Pageable}.
     *
     * <p>
     * Subclasses must implement this method to guarantee stable pagination even when no explicit sort
     * is requested. Without a deterministic sort, paginated queries may return inconsistent results
     * (rows skipped or duplicated across pages).
     * </p>
     *
     * @return a non-null {@link Sort} used as fallback when no sort is specified
     */
    protected abstract Sort defaultSort();

    /**
     * Creates a combined filter by merging the provided filter with the default {@link #defaultFilter()}.
     *
     * <p>
     * This method ensures that both the provided filter (such as a user-defined filter)
     * and the default filter (e.g., entity-level filter conditions) are applied during queries.
     * If both filters are {@code null}, this method returns {@code null}.
     * </p>
     *
     * @param filter the filter to apply, can be {@code null}.
     * @return a combined {@link FilterNode} if applicable, otherwise {@code null}.
     */
    protected @Nullable FilterNode combineWithDefaultFilter(@Nullable FilterNode filter) {
        final var combinedFilters = Stream.of(filter, defaultFilter())
            .filter(Objects::nonNull)
            .map(filterBuilder::from)
            .collect(Collectors.toCollection(ArrayList::new));
        return combinedFilters.isEmpty() ? null : filterBuilder.and(combinedFilters).get();
    }

    @Override
    public E create(Map<String, Object> properties) {
        E entity = objectMapper.convertValue(properties, entityClass);
        validate(entity);
        final var transaction = finalizeTransaction(transactionManager, transactionPolicy.forCreate());
        return Objects.requireNonNull(transaction.execute(ex -> {
            finalizeBeforeCreate(entity);
            E result = executeCreate(entity);
            finalizeAfterCreate(result);
            return result;
        }));
    }

    @Override
    public Page<E> read(@Nullable FilterNode filter, Pageable pageable) {
        final var transaction = finalizeTransaction(transactionManager, transactionPolicy.forRead());
        FilterNode combinedFilter = combineWithDefaultFilter(filter);
        Pageable effectivePageable = finalizeSort(pageable);
        Page<E> readResult = transaction.execute(ex -> {
            Page<E> result = executeRead(combinedFilter, effectivePageable);
            result.forEach(this::finalizeAfterRead);
            return result;
        });
        return Objects.requireNonNull(readResult);
    }

    private Pageable finalizeSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return applyDefaultSort(pageable);
        }
        Sort rewrittenSort = sortRewriter.rewrite(pageable.getSort(), entityClass);
        rewrittenSort.forEach(order -> validateSortProperty(order.getProperty()));
        if (rewrittenSort.equals(pageable.getSort())) {
            // Identity rewrite: keep the caller's Pageable (it may carry custom offset semantics).
            return pageable;
        }
        return pageable.isPaged()
            ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), rewrittenSort)
            : Pageable.unpaged(rewrittenSort);
    }

    private Pageable applyDefaultSort(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(defaultSort());
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort());
    }

    /**
     * Validates a single sort property, already translated to Java property names.
     *
     * <p>Backends override this hook to reject a property the underlying repository does not persist or cannot order
     * by, throwing a {@link DalInvalidSortException}; the default implementation accepts everything.
     * The DAL's own {@link #defaultSort()} is never routed through this hook.</p>
     *
     * @param property the dot-notation Java property path of one {@link Sort.Order}
     */
    protected void validateSortProperty(String property) {
        // noop
    }

    @Override
    public Optional<E> readOne(I id) {
        final var transaction = finalizeTransaction(transactionManager, transactionPolicy.forReadOne());
        Optional<E> readOneResult = transaction.execute(ex -> {
            Optional<E> result = executeReadOne(id);
            result.ifPresent(this::finalizeAfterReadOne);
            return result;
        });
        return Objects.requireNonNull(readOneResult);
    }

    @Override
    public Optional<E> update(I id, Map<String, Object> properties) {
        E updatableEntity = executeReadOne(id)
            .orElseThrow(() -> new DalEntityNotFoundException(this.getEntityClass(), id));
        propertyMerger.merge(properties, updatableEntity);
        validate(updatableEntity);
        final var transaction = finalizeTransaction(transactionManager, transactionPolicy.forUpdate());
        Optional<E> updateResult = transaction.execute(ex -> {
            finalizeBeforeUpdate(updatableEntity);
            E result = executeUpdate(updatableEntity);
            finalizeAfterUpdate(result);
            return readOne(id);
        });
        return Objects.requireNonNull(updateResult);
    }

    @Override
    public void delete(I id) {
        final var transaction = finalizeTransaction(transactionManager, transactionPolicy.forDelete());
        transaction.executeWithoutResult(ex -> {
            E entity = executeReadOne(id)
                .orElseThrow(() -> new DalEntityNotFoundException(this.getEntityClass(), id));
            finalizeBeforeDelete(entity);
            executeDelete(entity);
            finalizeAfterDelete(entity);
        });
    }

    /**
     * Finalizes the transaction template with the provided transaction manager and definition.
     *
     * @param transactionManager    the transaction manager to use
     * @param transactionDefinition the transaction definition to apply
     * @return a {@link TransactionTemplate} configured with the provided parameters
     */
    protected TransactionTemplate finalizeTransaction(
        PlatformTransactionManager transactionManager,
        DefaultTransactionDefinition transactionDefinition
    ) {
        return new TransactionTemplate(transactionManager, transactionDefinition);
    }

    /**
     * Hook to customize the entity before it is persisted.
     * Executed within the transactional context.
     *
     * @param entity the entity to customize before saving
     */
    protected void finalizeBeforeCreate(E entity) {
    }

    /**
     * Hook to execute logic after the entity has been created.
     * Executed within the transactional context.
     *
     * @param entity the created entity
     */
    protected void finalizeAfterCreate(E entity) {
    }

    /**
     * Hook to execute logic after the entity has been read.
     * Executed within the transactional context.
     *
     * @param entity the fetched entity
     */
    protected void finalizeAfterReadOne(E entity) {
    }

    /**
     * Hook called on each entity after a paged read operation.
     * Executed within the transactional context.
     *
     * @param entity the fetched entity from the page
     */
    protected void finalizeAfterRead(E entity) {
    }

    /**
     * Hook to execute logic before the entity has been updated.
     * Executed within the transactional context.
     *
     * @param entity the entity to customize before updating
     */
    protected void finalizeBeforeUpdate(E entity) {
    }

    /**
     * Hook to execute logic after the entity has been updated.
     * Executed within the transactional context.
     *
     * @param entity the updated entity
     */
    protected void finalizeAfterUpdate(E entity) {
    }

    /**
     * Hook to execute logic before the entity is deleted.
     * Executed within the transactional context.
     *
     * @param entity the entity being deleted, loaded through the filtered by-id lookup
     */
    protected void finalizeBeforeDelete(E entity) {
    }

    /**
     * Hook to execute logic after the entity has been deleted.
     * Executed within the transactional context.
     *
     * @param entity the deleted entity (no longer persistent)
     */
    protected void finalizeAfterDelete(E entity) {
    }

    /**
     * Persists the given entity.
     * Executed within the transactional context.
     *
     * @param entity the entity to create
     * @return the persisted entity
     */
    protected abstract E executeCreate(E entity);

    /**
     * Reads entities based on the provided filter and pagination.
     * Executed within the transactional context.
     *
     * @param filter   the filter to apply, can be {@code null}
     * @param pageable the pagination information
     * @return a page of entities matching the filter
     */
    protected abstract Page<E> executeRead(@Nullable FilterNode filter, Pageable pageable);

    /**
     * Reads an entity by its ID.
     * Executed within the transactional context when invoked via {@link #readOne(Object)} or as
     * {@link #delete(Object)}'s visibility pre-check (same transaction as the removal);
     * {@link #update(Object, Map)} invokes it directly, outside a transaction, before merging.
     *
     * <p><strong>Contract:</strong> implementations MUST honor {@link #defaultFilter()} — the
     * by-id lookup has to be combined with the default filter so that a hidden entity resolves
     * to an empty {@link Optional}. Both {@link #update(Object, Map)} and {@link #delete(Object)}
     * rely on this method as the single enforcement point of the default filter for by-id
     * operations.</p>
     *
     * @param id the ID of the entity to read
     * @return the entity with the given ID, or empty if it does not exist or is hidden by the
     * default filter
     */
    protected abstract Optional<E> executeReadOne(I id);

    /**
     * Updates the given entity.
     * Executed within the transactional context.
     *
     * @param entity the entity to update
     */
    protected abstract E executeUpdate(E entity);

    /**
     * Deletes the given entity.
     * Executed within the transactional context.
     *
     * <p>The entity is the instance just loaded through {@link #executeReadOne(Object)} within
     * the same transaction — the filtered visibility check and the removal share one transactional
     * snapshot. Implementations must delete <em>that instance</em> (for ORM-style backends: remove
     * the managed instance rather than issuing a by-id delete), so that an optimistic version
     * attribute — when the entity declares one — is honored and a concurrent modification fails
     * instead of deleting stale state. For backends without instance-identity deletes, the
     * residual race window for unversioned entities is backend- and isolation-dependent.</p>
     *
     * @param entity the entity to delete, previously loaded through the filtered by-id lookup
     */
    protected abstract void executeDelete(E entity);
}
