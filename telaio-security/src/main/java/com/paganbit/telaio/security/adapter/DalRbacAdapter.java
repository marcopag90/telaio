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
 * <p>Both methods are keyed by {@link DalOperationType} so a single implementation can differentiate
 * its behavior per operation (e.g., apply a stricter mask on update than on create) without widening
 * the contract. The default implementations are pass-through, so an implementor only overrides the
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
}
