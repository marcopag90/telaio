package com.paganbit.telaio.security.adapter;

import com.paganbit.telaio.core.adapter.DalOperationType;
import org.springframework.security.core.Authentication;

import java.util.Map;

/**
 * Contract for Role-Based Access Control (RBAC) field-level filtering.
 *
 * <p>Implementations sanitize request payloads before persistence and trim response entities before
 * they are returned, based on the current authenticated principal and the {@link DalOperationType}
 * being performed. Telaio exposes the entity directly (there is no DTO layer), so {@code T} is the
 * entity type.</p>
 *
 * <p>Both filtering methods are keyed by {@link DalOperationType} so a single implementation can
 * differentiate its behavior per operation.
 * A third hook, {@link #canFilterOn}, closes the read side: a property hidden from
 * the response must not be usable as a filter criterion either, or its value could be inferred from the
 * narrowed page. The default implementations are pass-through, so an implementor only overrides the
 * direction(s) it actually constrains; {@link NoopDalRbacAdapter} is exactly the no-op case.</p>
 *
 * @param <T> the exposed entity type
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalRbacAdapter<T> {

    /**
     * Applies RBAC constraints to a write request payload.
     *
     * <p>Invoked for write operations ({@link DalOperationType#CREATE} and
     * {@link DalOperationType#UPDATE}). Implementations should remove or transform disallowed fields
     * and return a map that is safe to persist.</p>
     *
     * @param operation      the operation being performed
     * @param input          the original payload as field/value pairs
     * @param authentication the current authentication context
     * @return a filtered payload that complies with the operation's write permissions
     */
    default Map<String, Object> filterInput(
        DalOperationType operation,
        Map<String, Object> input,
        Authentication authentication
    ) {
        return input;
    }

    /**
     * Applies RBAC constraints to a response entity.
     *
     * <p>Invoked for operations that return an entity ({@link DalOperationType#CREATE},
     * {@link DalOperationType#READ}, {@link DalOperationType#READ_ONE} and
     * {@link DalOperationType#UPDATE}); for read/list operations it is invoked once per returned entity.
     * Implementations should hide or alter sensitive properties, so callers only receive data allowed
     * by policy.</p>
     *
     * <p>The return type is {@code Object} — a <em>serialization-ready</em> representation of the
     * filtered entity, not necessarily an instance of {@code T}. A pass-through implementation returns
     * the entity unchanged ({@link NoopDalRbacAdapter}); a filtering implementation may return a
     * projection such as a Jackson tree ({@code JsonNode}). Returning a projection is deliberate:
     * reconstructing a partial {@code T} from a pruned tree would silently drop properties that Jackson
     * does not deserialize back onto a bean (e.g. {@code @JsonProperty(access = READ_ONLY)} fields such
     * as a generated {@code id}), even when those properties are visible to the principal. The result is
     * serialized directly to the wire, so the entity stays the boundary object (no DTO layer).</p>
     *
     * <p>An implementation that hides properties on read must also override {@link #canFilterOn}, or the
     * hidden fields can still be filtered on through {@code q=}.</p>
     *
     * @param operation      the operation being performed
     * @param entity         the entity produced by the operation (can be {@code null})
     * @param authentication the current authentication context
     * @return a serialization-ready view filtered according to the operation's read permissions, or
     * {@code null} when {@code entity} is {@code null}
     */
    default Object filterOutput(
        DalOperationType operation,
        T entity,
        Authentication authentication
    ) {
        return entity;
    }

    /**
     * Whether the principal may reference {@code fieldPath} in the filter of a
     * {@link DalOperationType#READ} operation.
     *
     * <p>Invoked once per field referenced by the caller's filter, before the read runs. Hiding a field
     * from the response is not enough: a principal could still filter on it and work out its value from
     * the rows that match. The rule to implement is simple — return {@code true} exactly for the fields
     * the principal can see in the read response. A denied field is rejected with the same generic
     * {@code 400 "Invalid filter expression"} as an unknown field, so the answer does not reveal whether
     * the field exists.</p>
     *
     * <p>{@code fieldPath} is the dot-notation path <em>as written in the filter</em>: it may use the JSON
     * wire name of a property as well as its Java name — both are accepted by the backends — so
     * implementations must resolve it against the entity rather than compare strings.</p>
     *
     * <p>The default accepts every field — a compatibility choice for adapters written before this hook
     * existed. An adapter that constrains {@link #filterOutput} must override it, or the fields it hides
     * can still be filtered on.</p>
     *
     * @param fieldPath      the field path referenced by the filter, as written by the caller
     * @param authentication the current authentication context
     * @return {@code true} to accept the reference, {@code false} to reject the whole filter
     * @since 1.2.0
     */
    default boolean canFilterOn(String fieldPath, Authentication authentication) {
        return true;
    }
}
