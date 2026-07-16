package com.paganbit.telaio.rest.contract.v1;

import com.paganbit.telaio.introspection.TypeUtil;
import com.paganbit.telaio.rest.contract.DalIdCodecException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Encodes and decodes DAL entity IDs to and from the {@code {id}} path segment.
 *
 * <ul>
 *   <li><strong>Simple</strong> types ({@code Long}, {@code String}, {@code UUID}, temporal
 *       types, …) travel as the raw path segment, converted through Jackson's
 *       {@code convertValue}.</li>
 *   <li><strong>Complex</strong> types (composite-key POJOs or records) travel as URL-safe,
 *       unpadded Base64 of their JSON serialization.</li>
 * </ul>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public final class DalIdCodec {

    private final ObjectMapper objectMapper;

    public DalIdCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Encodes an ID into its {@code {id}} path-segment representation.
     *
     * @param id     the ID value; must not be {@code null}
     * @param idType the declared ID type driving the simple-vs-complex classification
     * @return the path-segment representation (raw for simple types, URL-safe unpadded
     * Base64-encoded JSON for complex types)
     * @throws DalIdCodecException if the ID cannot be serialized
     */
    public String encode(Object id, Class<?> idType) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(idType, "idType must not be null");
        try {
            if (TypeUtil.isComplexType(idType)) {
                String json = objectMapper.writeValueAsString(id);
                return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
            }
            return Objects.requireNonNull(objectMapper.convertValue(id, String.class));
        } catch (RuntimeException e) {
            // The value itself stays out of the message (it may be sensitive or hostile and can
            // end up in logs); the cause chain carries the specifics.
            throw new DalIdCodecException(
                "Failed to encode ID of type %s".formatted(idType.getName()), e);
        }
    }

    /**
     * Decodes an {@code {id}} path segment into the declared ID type.
     *
     * @param rawId  the raw path-segment value; must not be {@code null}
     * @param idType the declared ID type driving the simple-vs-complex classification
     * @return the decoded ID instance
     * @throws DalIdCodecException if the segment is malformed (e.g. invalid Base64) or cannot be
     *                             deserialized into {@code idType}
     */
    public Object decode(String rawId, Class<?> idType) {
        Objects.requireNonNull(rawId, "rawId must not be null");
        Objects.requireNonNull(idType, "idType must not be null");
        if (TypeUtil.isComplexType(idType)) {
            String decodedJson = decodeFromBase64(rawId);
            try {
                return objectMapper.readValue(decodedJson, objectMapper.constructType(idType));
            } catch (RuntimeException e) {
                throw new DalIdCodecException("Failed to deserialize composite ID into %s"
                    .formatted(idType.getName()), e);
            }
        }
        try {
            return objectMapper.convertValue(rawId, objectMapper.constructType(idType));
        } catch (RuntimeException e) {
            // The raw segment is caller/attacker-controlled: keep it out of the message
            // (log-forging, sensitivity); the cause chain carries the specifics.
            throw new DalIdCodecException(
                "Failed to convert ID segment (length %d) into %s"
                    .formatted(rawId.length(), idType.getName()), e);
        }
    }

    private static String decodeFromBase64(String rawId) {
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(rawId);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new DalIdCodecException(
                "Failed to decode composite ID from Base64 (length %d)".formatted(rawId.length()), e);
        }
    }
}
