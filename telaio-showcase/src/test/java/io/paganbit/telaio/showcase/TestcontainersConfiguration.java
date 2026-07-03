package io.paganbit.telaio.showcase;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Provides a real PostgreSQL instance for integration tests through Testcontainers, matching the
 * {@code postgres:17} image used at runtime. The {@link ServiceConnection} annotation lets Spring Boot
 * derive the datasource connection details from the container automatically.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17");
    }
}
