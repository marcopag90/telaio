package com.paganbit.telaio.rest.client.internal;

import com.paganbit.telaio.rest.client.DalPage;
import com.paganbit.telaio.rest.client.exception.DalClientMalformedResponseException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Converts request payloads and response bodies of the DAL API v1 with its own pinned mapper:
 * deserialization is lenient regardless of the supplied mapper's configuration (the
 * response-leniency contract of {@code DalClient}), and request JSON never depends on the host
 * application's Jackson configuration (an app-wide {@code NON_NULL} inclusion must not drop an
 * explicit null-set from a {@link Map} payload).
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public final class DalPayloadCodec {

    private final ObjectMapper objectMapper;

    public DalPayloadCodec(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.objectMapper = objectMapper.rebuild()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            // Pinned although it is Jackson's current default: RBAC-stripped members must bind
            // record DTOs regardless of a future default flip or host configuration.
            .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            // The Map null-set contract (merge-patch "set to null") must survive any host
            // inclusion configuration when the payload tree is written.
            .enable(JsonNodeFeature.WRITE_NULL_PROPERTIES)
            .build();
    }

    /**
     * The lenient mapper shared by the client
     */
    public ObjectMapper mapper() {
        return objectMapper;
    }

    /**
     * Serializes a {@code create}/{@code update} input into its wire JSON: a {@link Map} keeps
     * its {@code null} values at every {@code Map} level (explicit null-set), any other object
     * (DTO) has its {@code null} members dropped recursively. Null preservation follows the
     * container type at each nesting level.
     */
    public String toWireJson(Object input) {
        Objects.requireNonNull(input, "input must not be null");
        JsonNode tree;
        if (input instanceof Map<?, ?> map) {
            tree = mapToNodePreservingNulls(map);
        } else {
            tree = objectMapper.valueToTree(input);
            stripNullMembers(tree);
        }
        return objectMapper.writeValueAsString(tree);
    }

    /**
     * Deserializes an entity node leniently into the target type.
     */
    public <E> E toEntity(JsonNode node, Class<E> entityType) {
        return objectMapper.treeToValue(node, entityType);
    }

    /**
     * Deserializes a {@code PagedModel}-shaped response into a {@link DalPage}, element by
     * element (two-step: tree first, entities second — unknown top-level members are ignored).
     *
     * @throws DalClientMalformedResponseException if the body is not a v1 page shape — the
     *                                             frozen wire contract makes drift conspicuous
     *                                             instead of fabricating page metadata
     */
    public <E> DalPage<E> toPage(@Nullable JsonNode root, Class<E> entityType) {
        if (root == null || !root.path("content").isArray() || !root.path("page").isObject()) {
            throw new DalClientMalformedResponseException(
                "The response is not a DAL API v1 page (missing 'content' array or 'page' metadata)");
        }
        List<E> content = new ArrayList<>();
        for (JsonNode element : root.path("content")) {
            content.add(toEntity(element, entityType));
        }
        DalPage.Metadata metadata = objectMapper.treeToValue(root.path("page"), DalPage.Metadata.class);
        return new DalPage<>(content, metadata);
    }

    private ObjectNode mapToNodePreservingNulls(Map<?, ?> map) {
        ObjectNode node = objectMapper.createObjectNode();
        map.forEach((key, value) -> {
            String member = String.valueOf(key);
            if (value == null) {
                node.putNull(member);
            } else {
                node.set(member, valueToNode(value));
            }
        });
        return node;
    }

    private JsonNode valueToNode(Object value) {
        if (value instanceof Map<?, ?> map) {
            return mapToNodePreservingNulls(map);
        }
        if (value instanceof Collection<?> collection) {
            ArrayNode array = objectMapper.createArrayNode();
            for (Object element : collection) {
                if (element == null) {
                    array.addNull();
                } else {
                    array.add(valueToNode(element));
                }
            }
            return array;
        }
        // Leaf values (scalars or nested DTOs) follow the DTO rule: nulls dropped.
        JsonNode node = objectMapper.valueToTree(value);
        stripNullMembers(node);
        return node;
    }

    private static void stripNullMembers(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<String> nullMembers = new ArrayList<>();
            objectNode.properties().forEach(property -> {
                if (property.getValue().isNull()) {
                    nullMembers.add(property.getKey());
                } else {
                    stripNullMembers(property.getValue());
                }
            });
            nullMembers.forEach(objectNode::remove);
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(DalPayloadCodec::stripNullMembers);
        }
    }
}
