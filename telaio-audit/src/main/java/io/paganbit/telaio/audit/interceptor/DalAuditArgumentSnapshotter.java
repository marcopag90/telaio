package io.paganbit.telaio.audit.interceptor;

import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import io.paganbit.telaio.core.adapter.DalOperationType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the per-operation argument snapshot recorded on audit events.
 *
 * <p>Property maps are copied defensively so later mutations do not alter the recorded event.
 * Filters are rendered back to their query form through the {@link FilterStringConverter} when
 * one is available; otherwise (or when rendering fails) the raw {@code toString()} value is
 * recorded — never at the expense of the business operation.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
final class DalAuditArgumentSnapshotter {

    private static final Logger log = LoggerFactory.getLogger(DalAuditArgumentSnapshotter.class);

    private final @Nullable FilterStringConverter filterStringConverter;

    DalAuditArgumentSnapshotter(@Nullable FilterStringConverter filterStringConverter) {
        this.filterStringConverter = filterStringConverter;
    }

    Map<String, Object> snapshot(DalOperationType operation, Object[] args) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        switch (operation) {
            case CREATE -> snapshot.put("input", copyOf(args[0]));
            case READ -> {
                snapshot.put("filter", args[0] != null ? renderFilter(args[0]) : null);
                snapshot.put("pageable", String.valueOf(args[1]));
            }
            case READ_ONE, DELETE -> snapshot.put("id", args[0]);
            case UPDATE -> {
                snapshot.put("id", args[0]);
                snapshot.put("patch", copyOf(args[1]));
            }
        }
        return snapshot;
    }

    private String renderFilter(Object filter) {
        if (filterStringConverter != null && filter instanceof FilterNode filterNode) {
            try {
                return filterStringConverter.convert(filterNode);
            } catch (Exception e) {
                log.debug("Failed to render filter for audit, falling back to toString()", e);
            }
        }
        return String.valueOf(filter);
    }

    private static Object copyOf(Object argument) {
        return argument instanceof Map<?, ?> map ? new LinkedHashMap<>(map) : argument;
    }
}
