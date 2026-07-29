package com.paganbit.telaio.rest.client.blocking;

import org.springframework.web.client.RestClient;

/**
 * Callback to customize a configured connection's {@link RestClient.Builder} before its client
 * is built by the autoconfiguration — the per-connection hook for authentication, SSL and any
 * other builder-level concern. All beans of this type run on every connection, in {@code @Order}
 * order, on top of Boot's pre-configured builder; use the connection name to scope:
 * <pre>{@code
 * @Bean
 * TelaioRestClientCustomizer billingAuth(OAuth2Interceptor interceptor) {
 *     return (name, builder) -> {
 *         if ("billing".equals(name)) {
 *             builder.requestInterceptor(interceptor);
 *         }
 *     };
 * }
 * }</pre>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
@FunctionalInterface
public interface TelaioRestClientCustomizer {

    /**
     * Customizes the {@link RestClient.Builder} of the named connection.
     *
     * @param connectionName the connection name ({@code telaio.rest-client.connections.<name>})
     * @param builder        the builder about to produce that connection's client
     */
    void customize(String connectionName, RestClient.Builder builder);
}
