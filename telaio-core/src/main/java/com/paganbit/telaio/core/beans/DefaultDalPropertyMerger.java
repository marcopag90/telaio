package com.paganbit.telaio.core.beans;

import org.springframework.util.CollectionUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link DalPropertyMerger} implementation based on Jackson.
 *
 * <p>Applies a (possibly partial and nested) property map onto an existing entity instance following
 * <a href="https://www.rfc-editor.org/rfc/rfc7396">JSON Merge Patch (RFC 7396)</a> semantics:</p>
 * <ul>
 *   <li>present scalar properties are set/replaced;</li>
 *   <li>nested objects are <strong>merged recursively</strong> onto the existing nested instance;</li>
 *   <li>collections, arrays, and maps are <strong>replaced wholesale</strong> (not merged element-wise);</li>
 *   <li>a {@code null} value clears the property;</li>
 *   <li>absent keys are left untouched.</li>
 * </ul>
 *
 * <p>The merge runs on a mapper <em>derived from the application {@link ObjectMapper}</em>, so naming
 * strategy, {@code @JsonProperty} renames, formats, and registered modules behave identically to the
 * create path ({@code ObjectMapper.convertValue}). Deep object merge is enabled globally
 * ({@code defaultMergeable}) and disabled for {@link Collection} and {@link Map} types to honor the
 * array-replacement rule of RFC 7396. Strictness on unknown properties is inherited from the source
 * mapper (so it matches the creation path).</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class DefaultDalPropertyMerger implements DalPropertyMerger {

    private final ObjectMapper mergeMapper;

    public DefaultDalPropertyMerger(ObjectMapper objectMapper) {
        // Deep-merge POJOs (RFC 7396), but replace containers wholesale. Jackson resolves the per-type
        // merge override by the property's declared raw type and does not cascade from Collection to its
        // subtypes, so the common container interfaces are registered explicitly.
        this.mergeMapper = objectMapper.rebuild()
            .defaultMergeable(true)
            .withConfigOverride(Collection.class, override -> override.setMergeable(false))
            .withConfigOverride(List.class, override -> override.setMergeable(false))
            .withConfigOverride(Set.class, override -> override.setMergeable(false))
            .withConfigOverride(Map.class, override -> override.setMergeable(false))
            .build();
    }

    /**
     * Merges the provided properties into the given target bean, in place, with RFC 7396 semantics.
     *
     * <p>If {@code properties} is {@code null} or empty, this method performs no action.</p>
     *
     * @param properties properties to apply; may include nested maps
     * @param target     target bean instance to update in place
     */
    @Override
    public void merge(Map<String, Object> properties, Object target) {
        if (CollectionUtils.isEmpty(properties)) {
            return;
        }
        mergeMapper.updateValue(target, properties);
    }
}
