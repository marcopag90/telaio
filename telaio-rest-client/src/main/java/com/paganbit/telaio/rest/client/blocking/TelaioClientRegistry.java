package com.paganbit.telaio.rest.client.blocking;

/**
 * Registry over the configured Telaio client connections
 * ({@code telaio.rest-client.connections.<name>}), resolving each name to a {@link TelaioClient}
 * bound to that connection's remote application.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public interface TelaioClientRegistry {

    /**
     * Returns the client of the named connection.
     *
     * @param connectionName the connection name as configured under
     *                       {@code telaio.rest-client.connections.<name>}
     * @return the client bound to that connection
     * @throws IllegalArgumentException if no such connection is configured
     */
    TelaioClient get(String connectionName);
}
