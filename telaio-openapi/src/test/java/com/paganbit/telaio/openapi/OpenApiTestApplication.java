package com.paganbit.telaio.openapi;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for the telaio-openapi integration tests. Component scanning picks up the
 * {@code @DalService} stub DALs under this package, while auto-configuration brings up the Telaio
 * core/web/openapi stack, springdoc, and Spring Filter's converters — all without a {@code DataSource}.
 */
@SpringBootApplication
class OpenApiTestApplication {
}
