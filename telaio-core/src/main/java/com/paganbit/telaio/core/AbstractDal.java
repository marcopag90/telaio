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
 * Backend-independent implementation of the {@link Dal} contract, built as a template method.
 *
 * <p>Each public operation runs the same pipeline: convert or merge the property map, validate the
 * entity, open the transaction the {@link DalTransactionPolicy} prescribes for that operation, call
 * the backend's {@code execute*} method, and surround it with the {@code finalize*} hooks. A backend
 * implements the five abstract {@code execute*} methods and {@link #defaultSort()}; a concrete DAL
 * may override the hooks, {@link #defaultFilter()}, and {@link #finalizeTransaction} to customize one
 * entity without touching the pipeline.</p>
 *
 * <p>Entity and identifier classes are resolved once from the subclass's type arguments, so a concrete
 * DAL must bind both ({@code class ProductDal extends JpaDal<Product, Long>}). Collaborators arrive
 * through setter injection and are checked in {@link #afterPropertiesSet()}: the class expects to be a
 * Spring-managed bean.</p>
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
     * The class of the entity this DAL manages, resolved from the subclass's first type argument.
     */
    protected final Class<E> entityClass;

    /**
     * The class of the entity identifier, resolved from the subclass's second type argument.
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
     * Builds and combines filter trees; used to narrow the caller's filter with the default filter.
     */
    protected FilterBuilder filterBuilder;

    /**
     * Parses filter strings into filter trees; kept available for backends and subclasses that
     * express a default filter as text.
     */
    protected FilterStringConverter filterStringConverter;

    //---------------------------------------------------------------------
    // Transaction Management
    //---------------------------------------------------------------------

    /**
     * The transaction manager every operation runs under.
     */
    protected PlatformTransactionManager transactionManager;

    /**
     * Supplies the transaction definition (propagation, isolation, read-only flag, timeout) of each
     * operation type.
     */
    protected DalTransactionPolicy transactionPolicy;

    /**
     * Resolves the entity and identifier classes from the concrete subclass's type arguments.
     *
     * @throws NullPointerException if the subclass does not bind both type arguments of
     *                              {@code AbstractDal}
     */
    protected AbstractDal() {
        this.entityClass = resolveEntityClass();
        this.idClass = resolveIdClass();
    }

    /**
     * Checks that every collaborator was injected before the first operation runs.
     *
     * <p>Collaborators are injected through setters rather than the constructor, so that backends and
     * concrete DALs keep their own constructors, and a test can replace one collaborator at a time.</p>
     *
     * @throws NullPointerException if a collaborator is missing
     */
    @Override
    public void afterPropertiesSet() {
        Objects.requireNonNull(objectMapper, "ObjectMapper must be set before using the service");
        Objects.requireNonNull(propertyMerger, "DalPropertyMerger must be set before using the service");
        Objects.requireNonNull(dalValidator, "DalValidator must be set before using the service");
        Objects.requireNonNull(filterBuilder, "FilterBuilder must be set before using the service");
        Objects.requireNonNull(filterStringConverter, "FilterStringConverter must be set before using the service");
        Objects.requireNonNull(transactionManager, "PlatformTransactionManager must be set before using the service");
        Objects.requireNonNull(transactionPolicy, "DalTransactionPolicy must be set before using the service");
        Objects.requireNonNull(sortRewriter, "JsonFieldNameSortRewriter must be set before using the service");
    }

    /**
     * Returns the mapper that converts property maps into entities.
     *
     * @return the object mapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Sets the mapper that converts property maps into entities.
     *
     * @param objectMapper the object mapper, not {@code null}
     */
    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    /**
     * Sets the rewriter that translates sort properties from JSON names to Java names.
     *
     * @param sortRewriter the sort rewriter, not {@code null}
     */
    @Autowired
    public void setSortRewriter(JsonFieldNameSortRewriter sortRewriter) {
        Objects.requireNonNull(sortRewriter, "JsonFieldNameSortRewriter must not be null");
        this.sortRewriter = sortRewriter;
    }

    /**
     * Returns the validator applied to every entity before it is written.
     *
     * @return the validator
     */
    public DalValidator getDalValidator() {
        return dalValidator;
    }

    /**
     * Sets the validator applied to every entity before it is written.
     *
     * @param dalValidator the validator, not {@code null}
     */
    @Autowired
    public void setDalValidator(DalValidator dalValidator) {
        Objects.requireNonNull(dalValidator, "DalValidator must not be null");
        this.dalValidator = dalValidator;
    }

    /**
     * Returns the merger that applies a property map to an existing entity on update.
     *
     * @return the property merger
     */
    public DalPropertyMerger getPropertyMerger() {
        return propertyMerger;
    }

    /**
     * Sets the merger that applies a property map to an existing entity on update.
     *
     * @param propertyMerger the property merger
     */
    @Autowired
    public void setPropertyMerger(DalPropertyMerger propertyMerger) {
        this.propertyMerger = propertyMerger;
    }

    /**
     * Returns the builder used to combine filter trees.
     *
     * @return the filter builder
     */
    public FilterBuilder getFilterBuilder() {
        return filterBuilder;
    }

    /**
     * Sets the builder used to combine filter trees.
     *
     * @param filterBuilder the filter builder, not {@code null}
     */
    @Autowired
    public void setFilterBuilder(FilterBuilder filterBuilder) {
        Objects.requireNonNull(filterBuilder, "FilterBuilder must not be null");
        this.filterBuilder = filterBuilder;
    }

    /**
     * Returns the converter that parses filter strings into filter trees.
     *
     * @return the filter string converter
     */
    public FilterStringConverter getFilterStringConverter() {
        return filterStringConverter;
    }

    /**
     * Sets the converter that parses filter strings into filter trees.
     *
     * @param filterStringConverter the filter string converter, not {@code null}
     */
    @Autowired
    public void setFilterStringConverter(FilterStringConverter filterStringConverter) {
        Objects.requireNonNull(filterStringConverter, "FilterStringConverter must not be null");
        this.filterStringConverter = filterStringConverter;
    }

    /**
     * Returns the transaction manager every operation runs under.
     *
     * @return the transaction manager
     */
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * Sets the transaction manager every operation runs under.
     *
     * @param transactionManager the transaction manager, not {@code null}
     */
    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        Objects.requireNonNull(transactionManager, "PlatformTransactionManager must not be null");
        this.transactionManager = transactionManager;
    }

    /**
     * Returns the policy that supplies the transaction definition of each operation type.
     *
     * @return the transaction policy
     */
    public DalTransactionPolicy getTransactionPolicy() {
        return transactionPolicy;
    }

    /**
     * Sets the policy that supplies the transaction definition of each operation type.
     *
     * @param transactionPolicy the transaction policy, not {@code null}
     */
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
     * Validates an entity against the constraints declared on {@link #getEntityClass()}.
     *
     * <p>The pipeline calls it on the converted entity before create and on the merged entity before
     * update; subclasses may call it from a hook when they change the entity afterward.</p>
     *
     * @param target the entity to validate
     * @throws DalEntityValidationException if the entity violates a constraint
     */
    public void validate(E target) throws DalEntityValidationException {
        this.dalValidator.validate(target, entityClass);
    }

    /**
     * Returns the filter that restricts which entities this DAL can see, or {@code null} for none.
     *
     * <p>This is how the pipeline implements the visibility rule of the {@link Dal} contract: an
     * entity the filter excludes is not visible, and every operation treats it as missing.</p>
     *
     * <p>The default filter constrains <strong>every operation</strong>: list reads (an entity must
     * match it and the caller's own filter, see {@link #combineWithDefaultFilter(FilterNode)}), by-id
     * reads (via {@link #executeReadOne(Object)}), and therefore also {@link #update(Object, Map)}
     * and {@link #delete(Object)}, which load the entity through the filtered by-id lookup. In every
     * case a hidden entity behaves exactly like a missing one. The filter is evaluated on every call,
     * so it may depend on request-scoped state such as the current tenant.</p>
     *
     * <p>The default implementation returns {@code null}: no restriction.</p>
     *
     * @return the default filter, or {@code null} when every entity is visible
     */
    protected @Nullable FilterNode defaultFilter() {
        return null;
    }

    /**
     * Returns the sort applied when the caller's {@link Pageable} is unsorted.
     *
     * <p>This is how the pipeline honors the stable-pages guarantee of {@link Dal#read} for unsorted
     * requests: without a deterministic order, consecutive pages may skip or repeat rows, so every
     * backend must provide one (typically by identifier). When the caller does supply a sort, that
     * sort is used as written, with no tie-breaker appended. The sort this method returns is applied
     * as-is: it is neither translated from JSON names nor passed through
     * {@link #validateSortProperty(String)}.</p>
     *
     * @return the fallback sort, never {@code null}
     */
    protected abstract Sort defaultSort();

    /**
     * Narrows the caller's filter with the {@linkplain #defaultFilter() default filter}: an entity
     * matches the result only if it matches both.
     *
     * @param filter the caller's filter, or {@code null} for none
     * @return the combined filter, the single non-null filter when only one applies, or {@code null}
     * when neither does
     */
    protected @Nullable FilterNode combineWithDefaultFilter(@Nullable FilterNode filter) {
        final var combinedFilters = Stream.of(filter, defaultFilter())
            .filter(Objects::nonNull)
            .map(filterBuilder::from)
            .collect(Collectors.toCollection(ArrayList::new));
        return combinedFilters.isEmpty() ? null : filterBuilder.and(combinedFilters).get();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the map into a new entity and validates it outside the transaction, then opens the
     * {@linkplain DalTransactionPolicy#forCreate() create transaction} and runs
     * {@link #finalizeBeforeCreate}, {@link #executeCreate}, and {@link #finalizeAfterCreate} inside
     * it.</p>
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>Narrows the filter with the {@linkplain #defaultFilter() default filter}, translates the
     * sort from JSON names to Java names (or applies {@link #defaultSort()} when unsorted), then opens the
     * {@linkplain DalTransactionPolicy#forRead() read transaction} and runs {@link #executeRead}
     * followed by {@link #finalizeAfterRead} on each entity of the page.</p>
     */
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
     * Validates one sort property, already translated to its Java name.
     *
     * <p>Backends override this hook to reject a property the underlying store does not persist or
     * cannot order by. The default implementation accepts everything. The DAL's own
     * {@link #defaultSort()} is never routed through this hook.</p>
     *
     * @param property the dot-notation Java property path of one {@link Sort.Order}
     * @throws DalInvalidSortException if the property cannot be used for sorting
     */
    protected void validateSortProperty(String property) {
        // noop
    }

    /**
     * {@inheritDoc}
     *
     * <p>Opens the {@linkplain DalTransactionPolicy#forReadOne() read-one transaction} and runs
     * {@link #executeReadOne} followed by {@link #finalizeAfterReadOne} when an entity is found.</p>
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>Loads the entity through the filtered {@link #executeReadOne}, merges the map into it, and
     * validates it, all outside a transaction; then opens the
     * {@linkplain DalTransactionPolicy#forUpdate() update transaction} and runs
     * {@link #finalizeBeforeUpdate}, {@link #executeUpdate}, and {@link #finalizeAfterUpdate} inside
     * it. The returned entity is read back through {@link #readOne}, within the same transaction, so
     * it reflects the persisted state and has gone through {@link #finalizeAfterReadOne}.</p>
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>Opens the {@linkplain DalTransactionPolicy#forDelete() delete transaction}; inside it, loads
     * the entity through the filtered {@link #executeReadOne}, then runs
     * {@link #finalizeBeforeDelete}, {@link #executeDelete}, and {@link #finalizeAfterDelete}. The
     * lookup and the removal share one transaction, so the visibility check cannot go stale between
     * them.</p>
     */
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
     * Creates the {@link TransactionTemplate} that runs one operation.
     *
     * <p>Called once per operation with the definition the {@link DalTransactionPolicy} returns for
     * it. Override to adjust the definition for one DAL, or to route an operation to another
     * transaction manager.</p>
     *
     * @param transactionManager    the DAL's transaction manager
     * @param transactionDefinition the definition the policy prescribes for the operation
     * @return the template the operation runs in
     */
    protected TransactionTemplate finalizeTransaction(
        PlatformTransactionManager transactionManager,
        DefaultTransactionDefinition transactionDefinition
    ) {
        return new TransactionTemplate(transactionManager, transactionDefinition);
    }

    /**
     * Hook run inside the create transaction, before {@link #executeCreate}.
     *
     * <p>The entity has already been converted and validated; a change made here is persisted without
     * further validation unless the hook calls {@link #validate} itself. The default implementation
     * does nothing.</p>
     *
     * @param entity the entity about to be persisted
     */
    protected void finalizeBeforeCreate(E entity) {
    }

    /**
     * Hook run inside the create transaction, after {@link #executeCreate}.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param entity the persisted entity, as returned by the backend
     */
    protected void finalizeAfterCreate(E entity) {
    }

    /**
     * Hook run inside the read-one transaction on the entity found by {@link #executeReadOne}.
     *
     * <p>Also reached by {@link #update}, which reads the updated entity back through
     * {@link #readOne}. The default implementation does nothing.</p>
     *
     * @param entity the entity found
     */
    protected void finalizeAfterReadOne(E entity) {
    }

    /**
     * Hook run inside the read transaction on each entity of the page returned by {@link #executeRead}.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param entity one entity of the page
     */
    protected void finalizeAfterRead(E entity) {
    }

    /**
     * Hook run inside the update transaction, before {@link #executeUpdate}.
     *
     * <p>The entity has already been merged with the caller's properties and validated; a change made
     * here is persisted without further validation unless the hook calls {@link #validate} itself.
     * The default implementation does nothing.</p>
     *
     * @param entity the merged entity about to be persisted
     */
    protected void finalizeBeforeUpdate(E entity) {
    }

    /**
     * Hook run inside the update transaction, after {@link #executeUpdate} and before the entity is
     * read back.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param entity the persisted entity, as returned by the backend
     */
    protected void finalizeAfterUpdate(E entity) {
    }

    /**
     * Hook run inside the delete transaction, before {@link #executeDelete}.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param entity the entity about to be deleted, as loaded through the filtered by-id lookup
     */
    protected void finalizeBeforeDelete(E entity) {
    }

    /**
     * Hook run inside the delete transaction, after {@link #executeDelete}.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param entity the deleted entity, no longer persistent
     */
    protected void finalizeAfterDelete(E entity) {
    }

    /**
     * Persists a new entity. Runs inside the create transaction.
     *
     * @param entity the converted and validated entity
     * @return the persisted entity, with any value assigned on insert
     */
    protected abstract E executeCreate(E entity);

    /**
     * Reads a page of entities. Runs inside the read transaction.
     *
     * @param filter   the filter to apply, already combined with the default filter; {@code null}
     *                 when neither the caller nor the DAL supplies one
     * @param pageable the page to read; its sort is never empty and is already expressed in Java
     *                 property names
     * @return the matching entities, never {@code null}
     */
    protected abstract Page<E> executeRead(@Nullable FilterNode filter, Pageable pageable);

    /**
     * Reads one entity by identifier, honoring the default filter.
     *
     * <p>Runs inside a transaction when reached through {@link #readOne(Object)} or as
     * {@link #delete(Object)}'s visibility check, and outside any transaction when
     * {@link #update(Object, Map)} loads the entity to merge.</p>
     *
     * <p><strong>Contract:</strong> implementations MUST combine the by-id lookup with
     * {@link #defaultFilter()}, so that a hidden entity resolves to an empty {@link Optional}. Both
     * {@link #update(Object, Map)} and {@link #delete(Object)} rely on this method as the single
     * enforcement point of the default filter for by-id operations.</p>
     *
     * @param id the entity identifier
     * @return the entity if one has that identifier and the default filter admits it, otherwise empty
     */
    protected abstract Optional<E> executeReadOne(I id);

    /**
     * Persists the changes made to an existing entity. Runs inside the update transaction.
     *
     * @param entity the merged and validated entity
     * @return the persisted entity, as the backend returns it after the write
     */
    protected abstract E executeUpdate(E entity);

    /**
     * Deletes an existing entity. Runs inside the delete transaction.
     *
     * <p>The entity is the instance just loaded through {@link #executeReadOne(Object)} within
     * the same transaction, so the filtered visibility check and the removal share one transactional
     * snapshot. Implementations must delete <em>that instance</em> (for ORM-style backends: remove
     * the managed instance rather than issuing a by-id delete), so that an optimistic version
     * attribute, when the entity declares one, is honored and a concurrent modification fails
     * instead of deleting stale state. For backends without instance-identity deletes, the
     * residual race window for unversioned entities is backend- and isolation-dependent.</p>
     *
     * @param entity the entity to delete, as loaded through the filtered by-id lookup
     */
    protected abstract void executeDelete(E entity);
}
