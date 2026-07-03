package io.paganbit.telaio.openapi.fixture;

/**
 * A complex (composite) identifier type, used to verify that the generated {@code id} path parameter
 * documents the Base64 URL-safe encoded JSON convention for non-simple ids.
 */
public record CompositeId(String tenant, String code) {
}
