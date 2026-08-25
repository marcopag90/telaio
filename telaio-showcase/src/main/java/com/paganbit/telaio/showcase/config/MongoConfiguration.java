package com.paganbit.telaio.showcase.config;

import com.paganbit.telaio.mongo.MongoDal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * MongoDB transaction setup for the showcase: production-grade multi-document transactions for the
 * Mongo-backed DALs (the docker-compose {@code mongo} service runs as a single-node replica set, a
 * server-side requirement for transactions).
 *
 * <p>The manager is declared under the Telaio qualifier {@link MongoDal#TRANSACTION_MANAGER_BEAN_NAME}
 * with {@code defaultCandidate = false}. In this mixed jpa+mongo application a plain
 * {@code MongoTransactionManager} bean would be a default-candidate {@code TransactionManager}: Spring
 * Boot's JPA autoconfiguration would then skip its {@code JpaTransactionManager} altogether and the
 * JPA DALs would bind to the Mongo manager (and, were the JPA manager declared explicitly as well, the
 * two candidates would break qualifier-less lookups such as the one telaio-metrics' JDBC store
 * performs). The qualified, non-default bean is invisible to Boot's condition and to by-type
 * autowiring, and visible only to the Mongo DALs' qualified injection point. The autoconfigured
 * fallback backs off because a bean with that name already exists.</p>
 */
@Configuration
public class MongoConfiguration {

    @Bean(name = MongoDal.TRANSACTION_MANAGER_BEAN_NAME, defaultCandidate = false)
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
