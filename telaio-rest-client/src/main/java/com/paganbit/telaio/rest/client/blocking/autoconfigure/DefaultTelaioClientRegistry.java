package com.paganbit.telaio.rest.client.blocking.autoconfigure;

import com.paganbit.telaio.rest.client.blocking.TelaioClient;
import com.paganbit.telaio.rest.client.blocking.TelaioClientRegistry;
import com.paganbit.telaio.rest.client.blocking.TelaioRestClient;
import com.paganbit.telaio.rest.client.blocking.TelaioRestClientCustomizer;
import com.paganbit.telaio.rest.client.config.TelaioRestClientProperties;
import com.paganbit.telaio.rest.client.internal.DalFilterStringConverters;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link TelaioClientRegistry} over the configured connections. Each client is built lazily,
 * once per connection:
 * <ol>
 *   <li>Spring Boot's autoconfigured {@link RestClient.Builder} is cloned, inheriting
 *       {@code spring.http.client.*} settings, SSL, observation and every
 *       {@code RestClientCustomizer};</li>
 *   <li>the connection's base URL and static default headers are applied;</li>
 *   <li>every {@link TelaioRestClientCustomizer} bean runs, in order;</li>
 *   <li>the resulting {@link RestClient} is handed to {@link TelaioRestClient#create}.</li>
 * </ol>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
class DefaultTelaioClientRegistry implements TelaioClientRegistry {

    private final TelaioRestClientProperties properties;
    private final ObjectProvider<RestClient.Builder> restClientBuilders;
    private final ObjectProvider<ObjectMapper> objectMapper;
    private final ObjectProvider<FilterStringConverter> filterStringConverters;
    private final ObjectProvider<TelaioRestClientCustomizer> customizers;
    private final Map<String, TelaioClient> clients = new ConcurrentHashMap<>();

    DefaultTelaioClientRegistry(
        TelaioRestClientProperties properties,
        ObjectProvider<RestClient.Builder> restClientBuilders,
        ObjectProvider<ObjectMapper> objectMapper,
        ObjectProvider<FilterStringConverter> filterStringConverters,
        ObjectProvider<TelaioRestClientCustomizer> customizers
    ) {
        this.properties = properties;
        this.restClientBuilders = restClientBuilders;
        this.objectMapper = objectMapper;
        this.filterStringConverters = filterStringConverters;
        this.customizers = customizers;
    }

    @Override
    public TelaioClient get(String connectionName) {
        Objects.requireNonNull(connectionName, "connectionName must not be null");
        TelaioRestClientProperties.Connection connection = properties.getConnections().get(connectionName);
        if (connection == null) {
            throw new IllegalArgumentException(
                "Unknown Telaio client connection '%s'; configure %s.connections.%s.base-url"
                    .formatted(connectionName, TelaioRestClientProperties.PREFIX, connectionName));
        }
        return clients.computeIfAbsent(connectionName, name -> build(name, connection));
    }

    private TelaioClient build(String connectionName, TelaioRestClientProperties.Connection connection) {
        String baseUrl = connection.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                "%s.connections.%s.base-url must be set"
                    .formatted(TelaioRestClientProperties.PREFIX, connectionName));
        }
        // Cloned defensively: Spring Boot's builder bean is prototype-scoped, a user-defined
        // singleton would otherwise accumulate every connection's base URL and headers.
        RestClient.Builder builder = restClientBuilders.getIfAvailable(RestClient::builder).clone();
        builder.baseUrl(baseUrl);
        connection.getDefaultHeaders().forEach((name, values) ->
            builder.defaultHeader(name, values.toArray(String[]::new)));
        customizers.orderedStream().forEach(customizer ->
            customizer.customize(connectionName, builder));

        RestClient restClient = builder.build();
        ObjectMapper mapper = objectMapper.getIfAvailable();
        // Turkraft's own autoconfiguration provides the converter in any Boot app; the fallback
        // only fires in contexts assembled without it.
        FilterStringConverter filterConverter =
            filterStringConverters.getIfAvailable(DalFilterStringConverters::pinned);
        return mapper != null
            ? TelaioRestClient.create(restClient, mapper, filterConverter)
            : TelaioRestClient.create(restClient, filterConverter);
    }
}
