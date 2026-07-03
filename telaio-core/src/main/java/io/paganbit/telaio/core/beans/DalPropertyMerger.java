package io.paganbit.telaio.core.beans;

import java.util.Map;

/**
 * Defines a contract to merge a map of properties into an existing entity instance.
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public interface DalPropertyMerger {

    /**
     * Merges the provided property values into the given target object.
     * Implementations are expected to map each property entry to its corresponding
     * field or setter on the target and apply type conversion when needed.
     *
     * @param properties the incoming field-value map
     * @param target     the object that receives the merged property values
     */
    void merge(Map<String, Object> properties, Object target);
}
