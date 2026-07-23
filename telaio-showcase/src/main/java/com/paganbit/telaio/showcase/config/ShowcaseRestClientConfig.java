package com.paganbit.telaio.showcase.config;

import com.paganbit.telaio.rest.client.blocking.TelaioRestClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;

/**
 * Wires the per-connection concerns of the Telaio REST client. The single {@code self} connection
 * (configured under {@code telaio.rest-client.connections}) points the app at its own DAL API for
 * the DAL-to-DAL round-trip demonstrated by {@code SupportTicketDalService}.
 *
 * <p>Authentication is supplied the idiomatic way — a {@link TelaioRestClientCustomizer} that adds a
 * {@link BasicAuthenticationInterceptor} to the {@code self} connection's builder. Credentials
 * default to the in-memory {@code user} account (username == password); override them with
 * {@code telaio.showcase.self-client.username/password}.
 */
@Configuration
public class ShowcaseRestClientConfig {

    @Bean
    TelaioRestClientCustomizer selfConnectionBasicAuth(
        @Value("${telaio.showcase.self-client.username:user}") String username,
        @Value("${telaio.showcase.self-client.password:user}") String password
    ) {
        return (connectionName, builder) -> {
            if ("self".equals(connectionName)) {
                builder.requestInterceptor(new BasicAuthenticationInterceptor(username, password));
            }
        };
    }
}
