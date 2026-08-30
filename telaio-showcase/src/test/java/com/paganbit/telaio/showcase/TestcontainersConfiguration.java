package com.paganbit.telaio.showcase;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Provides the two real databases the showcase runs on — PostgreSQL for the JPA DALs and MongoDB for
 * the {@code notifications} DAL — through Testcontainers, matching the {@code postgres:17} and
 * {@code mongo:8} images used at runtime. The {@link ServiceConnection} annotations let Spring Boot
 * derive the datasource and the Mongo connection details from the containers automatically.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17");
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        // withReplicaSet() is mandatory here: the showcase declares a real MongoTransactionManager
        // (MongoConfiguration), so every notifications operation runs in a multi-document
        // transaction, which a standalone mongod — the Testcontainers 2.x default — rejects.
        return new MongoDBContainer("mongo:8").withReplicaSet();
    }
}
