package com.paganbit.telaio.rest.client.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the Telaio REST client, bound to the {@code telaio.rest-client}
 * prefix: one named connection per remote Telaio application, resolved through the
 * {@code TelaioClientRegistry}.
 *
 * <pre>{@code
 * telaio:
 *   rest-client:
 *     connections:
 *       billing:
 *         base-url: https://billing.example.com
 * }</pre>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
// Deliberately NOT @Validated: Bean Validation constraints would make Spring Boot bootstrap a JSR-303
// validator whenever the jakarta.validation API is on the classpath — and crash the whole
// context (NoProviderFoundException) in the common case of API-without-provider. As a
// lightweight client library we validate programmatically instead: a missing base-url fails
// with a descriptive message when the connection is first resolved.
@ConfigurationProperties(TelaioRestClientProperties.PREFIX)
public class TelaioRestClientProperties {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "telaio.rest-client";

    /**
     * The connection name that, when present, backs the primary {@code TelaioClient} bean.
     */
    public static final String DEFAULT_CONNECTION_NAME = "default";

    /**
     * The named connections, one per remote Telaio application.
     */
    private final Map<String, Connection> connections = new LinkedHashMap<>();

    public Map<String, Connection> getConnections() {
        return connections;
    }

    /**
     * One remote Telaio application.
     *
     * @since 1.1.0
     */
    public static class Connection {

        /**
         * Base URL of the remote application ({@code scheme://host[:port][/context]}). Required:
         * its absence fails with a descriptive message when the connection is first resolved
         * (at startup for the primary connection).
         */
        private @Nullable String baseUrl;

        /**
         * Static headers sent with every request of this connection.
         */
        private final Map<String, List<String>> defaultHeaders = new LinkedHashMap<>();

        public @Nullable String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(@Nullable String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Map<String, List<String>> getDefaultHeaders() {
            return defaultHeaders;
        }
    }
}
