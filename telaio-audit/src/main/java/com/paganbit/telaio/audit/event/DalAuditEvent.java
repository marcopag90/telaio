package com.paganbit.telaio.audit.event;

import com.paganbit.telaio.core.adapter.DalOperationType;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * An immutable record of a single audited DAL operation.
 *
 * <p>All components are simple, serialization-friendly values, so events can be persisted by any
 * {@link DalAuditEventStore} implementation. The argument values are recorded as supplied by the
 * caller of the audited component — for web requests this means RBAC-filtered values at the DAL
 * level and raw request values for denied attempts — and may contain sensitive property values.</p>
 *
 * @param timestamp    when the operation started
 * @param dalName      the registration name of the DAL
 * @param operation    the CRUD operation that was invoked
 * @param principal    the name of the authenticated principal, or {@code null} when the invocation
 *                     was unauthenticated (for example, a programmatic or messaging-driven call)
 * @param arguments    a per-operation snapshot of the invocation arguments, keyed by parameter
 *                     role ({@code input}, {@code id}, {@code patch}, {@code filter},
 *                     {@code pageable})
 * @param outcome      how the operation ended
 * @param errorType    the fully qualified class name of the failure, or {@code null} on success
 * @param errorMessage the failure message, or {@code null} on success
 * @param duration     how long the operation took, including all inner concerns
 * @author Marco Pagan
 * @since 1.0.0
 */
public record DalAuditEvent(
    Instant timestamp,
    String dalName,
    DalOperationType operation,
    @Nullable String principal,
    Map<String, Object> arguments,
    DalAuditOutcome outcome,
    @Nullable String errorType,
    @Nullable String errorMessage,
    Duration duration
) {
}
